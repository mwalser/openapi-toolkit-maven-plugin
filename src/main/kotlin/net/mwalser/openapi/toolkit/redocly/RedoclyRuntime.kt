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
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Hosts the embedded Redocly JavaScript bundle in a GraalJS context and runs commands against it.
 *
 * A runtime is expensive to create (the bundle has to be evaluated), so [shared] keeps one instance per
 * [EngineMode] for the lifetime of the JVM. Instances are thread-safe; calls are serialized because a
 * JavaScript context is single-threaded.
 */
class RedoclyRuntime private constructor(
    /** The engine that is actually in use after resolving [EngineMode.AUTO]. */
    val effectiveEngine: EffectiveEngine,
    private val engine: Engine,
    private val context: Context,
    private val bridge: HostBridge,
    module: Value,
) : AutoCloseable {

    enum class EffectiveEngine { JIT, ISOLATE, INTERPRETER }

    private val lock = ReentrantLock()
    private val drainTimers: Value = module.getMember("drainTimers")
    private val run: Value = module.getMember("run")

    /** Version of `@redocly/openapi-core` embedded in the bundle. */
    val redoclyVersion: String = module.getMember("version").execute().asString()

    /**
     * Runs a command of the JavaScript API layer. [options] is serialized to JSON, the result is
     * deserialized into [resultType]. Logging produced while the command runs goes to [log].
     */
    fun <T> run(command: String, options: Any, resultType: Class<T>, workingDirectory: Path, log: JsLog): T = lock.withLock {
        bridge.log = log
        bridge.workingDirectory = workingDirectory.toAbsolutePath().normalize()
        try {
            val optionsJson = Json.mapper.writeValueAsString(options)
            val promise = try {
                run.execute(command, optionsJson)
            } catch (e: PolyglotException) {
                throw toException(e)
            }
            val resultJson = await(promise).asString()
            Json.mapper.readValue(resultJson, resultType)
        } finally {
            bridge.log = JsLog.SILENT
        }
    }

    inline fun <reified T> run(command: String, options: Any, workingDirectory: Path, log: JsLog): T =
        run(command, options, T::class.java, workingDirectory, log)

    /** Blocks until the promise settles. Queued timers are drained between microtask checkpoints. */
    private fun await(promise: Value): Value {
        var result: Value? = null
        var rejection: Value? = null
        var settled = false
        promise.invokeMember(
            "then",
            ProxyExecutable { args -> result = args[0]; settled = true; null },
            ProxyExecutable { args -> rejection = args[0]; settled = true; null },
        )
        while (!settled) {
            val ran = try {
                drainTimers.execute().asInt()
            } catch (e: PolyglotException) {
                throw toException(e)
            }
            if (ran == 0 && !settled) {
                throw RedoclyException("Internal error: the JavaScript promise never settled (no pending timers)")
            }
        }
        rejection?.let { throw toException(it) }
        return result!!
    }

    private fun toException(value: Value): RedoclyException {
        if (value.isString) return RedoclyException(value.asString())
        val name = value.takeIf { it.hasMember("name") }?.getMember("name")?.takeIf { it.isString }?.asString()
        val message = value.takeIf { it.hasMember("message") }?.getMember("message")?.takeIf { it.isString }?.asString()
        val stack = value.takeIf { it.hasMember("stack") }?.getMember("stack")?.takeIf { it.isString }?.asString()
        return RedoclyException(message ?: value.toString(), jsName = name, jsStack = stack)
    }

    private fun toException(e: PolyglotException): RedoclyException =
        if (e.isGuestException && e.guestObject != null) toException(e.guestObject).also { it.initCause(e) }
        else RedoclyException(e.message ?: "JavaScript error", cause = e)

    override fun close() {
        lock.withLock {
            context.close(true)
            engine.close(true)
        }
    }

    companion object {
        private const val BUNDLE_RESOURCE = "redocly-core.mjs"
        // Intentionally never closed: the runtime lives as long as the plugin classloader (a shutdown hook cannot
        // run safely because Maven disposes the plugin realm before JVM exit; the OS reclaims the resources).
        private val sharedRuntimes = ConcurrentHashMap<EngineMode, RedoclyRuntime>()

        /** Returns the JVM-wide runtime for [mode], creating it on first use. */
        fun shared(mode: EngineMode, diagnostics: JsLog = JsLog.SILENT): RedoclyRuntime =
            sharedRuntimes.computeIfAbsent(mode) { create(it, diagnostics) }

        /** Creates a new, independent runtime. Prefer [shared] unless isolation is required. */
        fun create(mode: EngineMode, diagnostics: JsLog = JsLog.SILENT): RedoclyRuntime {
            val (effective, engine) = createEngine(mode, diagnostics)
            val bridge = HostBridge()
            val contextBuilder = Context.newBuilder("js")
                .engine(engine)
                .option("js.esm-eval-returns-exports", "true")
            if (effective == EffectiveEngine.ISOLATE) {
                contextBuilder.allowHostAccess(HostAccess.SCOPED)
            }
            val context = contextBuilder.build()
            try {
                context.getBindings("js").putMember("__jvm", bridge.asProxy())
                val module = context.eval(loadBundle())
                val runtime = RedoclyRuntime(effective, engine, context, bridge, module)
                diagnostics.info("Redocly ${runtime.redoclyVersion} - JavaScript engine: ${describe(effective)}")
                return runtime
            } catch (e: PolyglotException) {
                context.close(true)
                engine.close(true)
                throw RedoclyException("Failed to initialize the embedded Redocly bundle: ${e.message}", cause = e)
            }
        }

        /**
         * Whether a polyglot isolate for JavaScript (`org.graalvm.polyglot:js-isolate-<os>-<arch>-community`) is on
         * the classpath. Detected by its Truffle resource provider registration, without starting it.
         */
        fun isIsolatePresent(): Boolean {
            val loader = RedoclyRuntime::class.java.classLoader ?: ClassLoader.getSystemClassLoader()
            val services = loader.getResources("META-INF/services/com.oracle.truffle.api.provider.InternalResourceProvider")
            for (url in services) {
                val providers = url.openStream().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                if (providers.lineSequence().any { it.contains("isolate") && it.contains(".js.") }) return true
            }
            return false
        }

        /** The platform suffix of the isolate artifact matching this JVM, e.g. `linux-amd64`. */
        fun isolatePlatform(): String {
            val osName = System.getProperty("os.name", "").lowercase()
            val os = when {
                osName.contains("win") -> "windows"
                osName.contains("mac") || osName.contains("darwin") -> "darwin"
                else -> "linux"
            }
            val arch = when (System.getProperty("os.arch", "").lowercase()) {
                "amd64", "x86_64" -> "amd64"
                "aarch64", "arm64" -> "aarch64"
                else -> System.getProperty("os.arch", "unknown")
            }
            return "$os-$arch"
        }

        private fun createEngine(mode: EngineMode, diagnostics: JsLog): Pair<EffectiveEngine, Engine> {
            val effective = when (mode) {
                EngineMode.INTERPRETER -> EffectiveEngine.INTERPRETER
                EngineMode.ISOLATE -> EffectiveEngine.ISOLATE
                EngineMode.AUTO -> when {
                    Engine.supportsCompilation() -> EffectiveEngine.JIT
                    isIsolatePresent() -> EffectiveEngine.ISOLATE
                    else -> EffectiveEngine.INTERPRETER
                }
            }
            diagnostics.debug("Redocly engine mode $mode resolved to $effective")
            val engine = try {
                newEngineBuilder(isolate = effective == EffectiveEngine.ISOLATE).build()
            } catch (e: Exception) {
                if (effective == EffectiveEngine.ISOLATE) {
                    val hint = if (isIsolatePresent()) {
                        "A JavaScript polyglot isolate is on the classpath but could not be started. Check that the artifact matches " +
                            "this platform (expected org.graalvm.polyglot:js-isolate-${isolatePlatform()}-community) or remove it to fall back to the interpreter."
                    } else {
                        "No JavaScript polyglot isolate is on the classpath. Add org.graalvm.polyglot:js-isolate-${isolatePlatform()}-community " +
                            "as a dependency of the plugin."
                    }
                    throw RedoclyException("$hint Cause: ${e.message}", cause = e)
                }
                throw RedoclyException("Failed to create the JavaScript engine: ${e.message}", cause = e)
            }
            return effective to engine
        }

        private fun describe(engine: EffectiveEngine): String = when (engine) {
            EffectiveEngine.JIT -> "GraalJS with runtime compilation"
            EffectiveEngine.ISOLATE -> "GraalJS native isolate"
            EffectiveEngine.INTERPRETER ->
                "GraalJS interpreter (add org.graalvm.polyglot:js-isolate-${isolatePlatform()}-community to the plugin dependencies for faster runs)"
        }

        private fun newEngineBuilder(isolate: Boolean): Engine.Builder {
            val builder = Engine.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
            if (isolate) {
                builder.spawnIsolate(true)
            }
            if (isolate || Engine.supportsCompilation()) {
                // one-shot workloads: favor fast warm-up over peak performance
                builder.option("engine.Mode", "latency")
            }
            return builder
        }

        private fun loadBundle(): Source {
            val stream = RedoclyRuntime::class.java.getResourceAsStream(BUNDLE_RESOURCE)
                ?: throw RedoclyException("Embedded bundle $BUNDLE_RESOURCE not found on the classpath")
            InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                return Source.newBuilder("js", reader, BUNDLE_RESOURCE)
                    .mimeType("application/javascript+module")
                    .cached(true)
                    .build()
            }
        }
    }
}
