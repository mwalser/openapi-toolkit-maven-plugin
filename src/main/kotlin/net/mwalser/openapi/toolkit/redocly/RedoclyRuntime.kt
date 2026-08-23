package net.mwalser.openapi.toolkit.redocly

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Hosts the embedded Redocly JavaScript bundle in a GraalJS context and runs commands against it.
 *
 * A runtime is expensive to create (the bundle has to be evaluated), so [shared] keeps one instance per
 * [EngineMode] for the lifetime of the JVM. Every interaction with the context happens on one dedicated
 * thread, which serializes callers (a JavaScript context is single-threaded) and provides the deep stack
 * that resolving long `$ref` chains needs.
 */
class RedoclyRuntime private constructor(
    /** The engine that is actually in use after resolving [EngineMode.AUTO]. */
    val effectiveEngine: EffectiveEngine,
    private val engine: Engine,
    private val context: Context,
    private val bridge: HostBridge,
    module: Value,
    private val jsThread: ExecutorService,
) : AutoCloseable {

    enum class EffectiveEngine { JIT, ISOLATE, INTERPRETER }

    private val run: Value = module.getMember("run")

    /** Version of `@redocly/openapi-core` embedded in the bundle. */
    val redoclyVersion: String = module.getMember("version").execute().asString()

    /**
     * Runs a command of the JavaScript API layer. [options] is serialized to JSON, the result is deserialized
     * into [resultType]. Relative paths are resolved against [CommandOptions.cwd]; logging produced while the
     * command runs goes to [log].
     */
    fun <T> run(command: String, options: CommandOptions, resultType: Class<T>, log: JsLog, network: NetworkConfig = NetworkConfig()): T =
        jsThread.call {
            bridge.log = log
            bridge.workingDirectory = JsPaths.toHostPath(options.cwd).toAbsolutePath().normalize()
            bridge.network = network
            try {
                val promise = guest { run.execute(command, Json.mapper.writeValueAsString(options)) }
                Json.mapper.readValue(settledValue(promise).asString(), resultType)
            } finally {
                bridge.log = JsLog.SILENT
            }
        }

    inline fun <reified T> run(command: String, options: CommandOptions, log: JsLog, network: NetworkConfig = NetworkConfig()): T =
        run(command, options, T::class.java, log, network)

    /**
     * Unwraps the promise returned by `run`. It has settled by the time `execute` returns: the bundle has no
     * event loop, and its only timer — openapi-core's stack-unwinding `setTimeout` — is a microtask (see
     * `js/src/polyfills.js`), so GraalJS drains every pending callback before control returns to the host.
     */
    private fun settledValue(promise: Value): Value {
        var outcome: Value? = null
        var rejected = false
        promise.invokeMember(
            "then",
            ProxyExecutable { args -> outcome = args[0]; null },
            ProxyExecutable { args -> outcome = args[0]; rejected = true; null },
        )
        val value = outcome ?: throw RedoclyException("Internal error: the JavaScript promise did not settle")
        if (rejected) throw toException(value)
        return value
    }

    private inline fun <T> guest(body: () -> T): T = try {
        body()
    } catch (e: PolyglotException) {
        throw toException(e)
    }

    private fun toException(e: PolyglotException): RedoclyException = when {
        e.isGuestException && e.guestObject != null -> toException(e.guestObject).also { it.initCause(e) }
        e.isHostException -> RedoclyException(e.message ?: "JavaScript error", cause = e.asHostException())
        else -> RedoclyException(e.message ?: "JavaScript error", cause = e)
    }

    /** Converts a rejection value (`{ name, message, stack, details }` from `run`, or anything thrown) to an exception. */
    private fun toException(error: Value): RedoclyException {
        if (error.isString) return RedoclyException(error.asString())
        val message = error.stringMember("message") ?: error.toString()
        val output = error.member("details")?.stringMember("output")?.trimEnd().orEmpty()
        return RedoclyException(
            message = if (output.isEmpty()) message else "$output\n$message",
            jsName = error.stringMember("name"),
            jsStack = error.stringMember("stack"),
        )
    }

    override fun close() {
        jsThread.call {
            context.close(true)
            engine.close(true)
        }
        jsThread.shutdown()
    }

    companion object {
        private const val BUNDLE_RESOURCE = "redocly-core.mjs"

        /** GraalJS recursion depth is bounded by the thread stack; Maven's 1 MB main thread allows only ~700 frames. */
        private const val JS_THREAD_STACK_BYTES = 256L * 1024 * 1024

        // Intentionally never closed: the runtime lives as long as the plugin classloader (a shutdown hook cannot
        // run safely because Maven disposes the plugin realm before JVM exit; the OS reclaims the resources).
        private val sharedRuntimes = ConcurrentHashMap<EngineMode, RedoclyRuntime>()

        /** Returns the JVM-wide runtime for [mode], creating it on first use. */
        @JvmStatic
        fun shared(mode: EngineMode, diagnostics: JsLog = JsLog.SILENT): RedoclyRuntime =
            sharedRuntimes.computeIfAbsent(mode) { create(it, diagnostics) }

        /** Creates a new, independent runtime. Prefer [shared] unless isolation is required. */
        @JvmStatic
        fun create(mode: EngineMode, diagnostics: JsLog = JsLog.SILENT): RedoclyRuntime {
            val jsThread = Executors.newSingleThreadExecutor { task ->
                Thread(null, task, "openapi-toolkit-js", JS_THREAD_STACK_BYTES).apply { isDaemon = true }
            }
            val runtime = try {
                jsThread.call { initialize(mode, diagnostics, jsThread) }
            } catch (e: Exception) {
                jsThread.shutdown()
                throw e
            }
            diagnostics.info("Redocly ${runtime.redoclyVersion} - JavaScript engine: ${describe(runtime.effectiveEngine)}")
            return runtime
        }

        /** Builds engine and context and evaluates the bundle; runs on the JavaScript thread. */
        private fun initialize(mode: EngineMode, diagnostics: JsLog, jsThread: ExecutorService): RedoclyRuntime {
            val effective = resolve(mode)
            diagnostics.debug("Redocly engine mode $mode resolved to $effective")
            val engine = buildEngine(effective)
            try {
                val context = Context.newBuilder("js")
                    .engine(engine)
                    .allowHostAccess(HostAccess.NONE)
                    .option("js.esm-eval-returns-exports", "true")
                    .option("js.performance", "true")
                    .option("js.print", "false")
                    .option("js.load", "false")
                    .build()
                val bridge = HostBridge()
                context.getBindings("js").putMember("__jvm", bridge.asProxy())
                val module = context.eval(loadBundle())
                return RedoclyRuntime(effective, engine, context, bridge, module, jsThread)
            } catch (e: Exception) {
                engine.close(true)
                if (e is PolyglotException) throw RedoclyException("Failed to initialize the embedded Redocly bundle: ${e.message}", cause = e)
                throw e
            }
        }

        /**
         * Whether a polyglot isolate for JavaScript (`org.graalvm.polyglot:js-isolate-<os>-<arch>-community`) is on
         * the classpath. Detected by its Truffle resource provider registration, without starting it.
         */
        fun isIsolatePresent(): Boolean {
            val loader = RedoclyRuntime::class.java.classLoader ?: ClassLoader.getSystemClassLoader()
            val registrations = loader.getResources("META-INF/services/com.oracle.truffle.api.provider.InternalResourceProvider")
            return registrations.asSequence().any { url ->
                val providers = url.openStream().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                providers.lineSequence().any { it.contains("isolate") && it.contains(".js.") }
            }
        }

        /** The platform suffix of the isolate artifact matching this JVM, e.g. `linux-amd64`. */
        fun isolatePlatform(): String {
            val osName = System.getProperty("os.name", "").lowercase()
            val os = when {
                osName.contains("win") -> "windows"
                osName.contains("mac") || osName.contains("darwin") -> "darwin"
                else -> "linux"
            }
            val arch = when (val archName = System.getProperty("os.arch", "unknown").lowercase()) {
                "amd64", "x86_64" -> "amd64"
                "aarch64", "arm64" -> "aarch64"
                else -> archName
            }
            return "$os-$arch"
        }

        private fun resolve(mode: EngineMode): EffectiveEngine = when (mode) {
            EngineMode.INTERPRETER -> EffectiveEngine.INTERPRETER
            EngineMode.ISOLATE -> EffectiveEngine.ISOLATE
            EngineMode.AUTO -> when {
                Engine.supportsCompilation() -> EffectiveEngine.JIT
                isIsolatePresent() -> EffectiveEngine.ISOLATE
                else -> EffectiveEngine.INTERPRETER
            }
        }

        private fun buildEngine(effective: EffectiveEngine): Engine {
            val builder = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false")
            if (effective == EffectiveEngine.ISOLATE) builder.spawnIsolate(true)
            return try {
                builder.build()
            } catch (e: Exception) {
                throw RedoclyException(engineFailureMessage(effective, e), cause = e)
            }
        }

        private fun engineFailureMessage(effective: EffectiveEngine, failure: Exception): String {
            val artifact = "org.graalvm.polyglot:js-isolate-${isolatePlatform()}-community"
            val problem = when {
                effective != EffectiveEngine.ISOLATE -> "Failed to create the JavaScript engine."
                isIsolatePresent() ->
                    "A JavaScript polyglot isolate is on the classpath but could not be started. Check that the artifact matches " +
                        "this platform (expected $artifact) or remove it to fall back to the interpreter."
                else -> "No JavaScript polyglot isolate is on the classpath. Add $artifact as a dependency of the plugin."
            }
            return "$problem Cause: ${failure.message}"
        }

        private fun describe(engine: EffectiveEngine): String = when (engine) {
            EffectiveEngine.JIT -> "GraalJS with runtime compilation"
            EffectiveEngine.ISOLATE -> "GraalJS native isolate"
            EffectiveEngine.INTERPRETER ->
                "GraalJS interpreter (add org.graalvm.polyglot:js-isolate-${isolatePlatform()}-community to the plugin dependencies for faster runs)"
        }

        private fun loadBundle(): Source {
            val stream = RedoclyRuntime::class.java.getResourceAsStream(BUNDLE_RESOURCE)
                ?: throw RedoclyException("Embedded bundle $BUNDLE_RESOURCE not found on the classpath")
            return InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                Source.newBuilder("js", reader, BUNDLE_RESOURCE)
                    .mimeType("application/javascript+module")
                    .cached(true)
                    .build()
            }
        }
    }
}

/** Runs [action] on this executor and rethrows its failure unwrapped. */
private fun <T> ExecutorService.call(action: () -> T): T = try {
    submit(Callable(action)).get()
} catch (e: ExecutionException) {
    throw e.cause ?: e
}

private fun Value.member(name: String): Value? = if (hasMember(name)) getMember(name) else null

private fun Value.stringMember(name: String): String? = member(name)?.takeIf { it.isString }?.asString()
