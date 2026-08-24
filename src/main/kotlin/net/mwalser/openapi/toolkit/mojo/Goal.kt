package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.ConfigLintResult
import net.mwalser.openapi.toolkit.redocly.EngineMode
import net.mwalser.openapi.toolkit.redocly.JsPaths
import net.mwalser.openapi.toolkit.redocly.NetworkConfig
import net.mwalser.openapi.toolkit.redocly.ProxyConfig
import net.mwalser.openapi.toolkit.redocly.Redocly
import net.mwalser.openapi.toolkit.redocly.RedoclyException
import net.mwalser.openapi.toolkit.redocly.RedoclyRuntime
import net.mwalser.openapi.toolkit.redocly.Totals
import net.mwalser.openapi.toolkit.redocly.UnusedConfig
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * What a goal does. The Java mojo classes only declare the parameters; each of them hands itself to its
 * `Goal`, which validates the parameters, runs the Redocly command and reports the outcome.
 */
abstract class Goal<M : AbstractRedoclyMojo>(protected val mojo: M) {

    protected val log: Log get() = mojo.log

    /** The goal's own skip parameter; `openapi.skip` additionally skips every goal. */
    protected abstract val skipGoal: SkipParameter

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    fun execute() {
        val skippedBy = when {
            mojo.skip -> "openapi.skip"
            skipGoal.enabled -> skipGoal.property
            else -> null
        }
        if (skippedBy != null) {
            log.info("Skipping ($skippedBy=true)")
            return
        }
        validate()
        try {
            run()
        } catch (e: RedoclyException) {
            e.jsStack?.let(log::debug)
            throw MojoExecutionException(e.message, e)
        }
    }

    /**
     * Checks parameter values before anything is executed. Overrides must call `super.validate()`.
     * Invalid values are reported as [MojoExecutionException] naming the property.
     */
    protected open fun validate() {}

    protected abstract fun run()

    protected fun requireOneOf(property: String, value: String, allowed: Collection<String>) {
        if (value !in allowed) {
            throw MojoExecutionException("Invalid value '$value' for $property; expected one of: ${allowed.joinToString(", ")}")
        }
    }

    protected val basedir: Path get() = mojo.project.basedir.toPath()

    /** The base directory as a JS path (what the JS side uses as `cwd`). */
    protected val jsCwd: String get() = JsPaths.toJs(basedir)

    protected fun resolve(file: File): Path = basedir.resolve(file.toPath()).normalize()

    protected fun jsPath(path: Path): String = JsPaths.toJs(path)

    protected fun hostPath(jsPath: String): Path = JsPaths.toHostPath(jsPath)

    /** Displays a path reported by the JS side relative to the base directory when possible. */
    protected fun display(jsPath: String): String {
        val host = JsPaths.toHost(jsPath)
        return runCatching { display(Path.of(host)) }.getOrDefault(host)
    }

    protected fun display(path: Path): String =
        if (path.startsWith(basedir)) basedir.relativize(path).toString() else path.toString()

    /** Redocly bound to this goal's log and to the build's offline flag and proxy. The runtime is shared JVM-wide. */
    protected fun redocly(): Redocly {
        val jsLog = MavenJsLog(log)
        val runtime = RedoclyRuntime.shared(EngineMode.AUTO, jsLog)
        return Redocly(runtime, jsLog, NetworkConfig(offline = mojo.session.isOffline, proxy = activeProxy()))
    }

    private fun activeProxy(): ProxyConfig? {
        val configured = mojo.session.settings.activeProxy ?: return null
        val proxy = mojo.settingsDecrypter.decrypt(DefaultSettingsDecryptionRequest(configured)).proxy
        return ProxyConfig(proxy.host, proxy.port, proxy.username, proxy.password, proxy.nonProxyHosts)
    }

    /** Prints the result of linting the configuration file and fails the build on configuration errors. */
    protected fun reportConfigLint(
        result: ConfigLintResult?,
        configFile: Path?,
        format: String = "stylish",
        hint: String = "Fix the configuration or set lintConfig=off.",
    ) {
        val totals = result?.totals?.takeIf { it.hasProblems } ?: return
        printProblems(result.output, format, if (totals.errors > 0) MavenJsLog.Level.ERROR else MavenJsLog.Level.WARN)
        val summary = "Configuration file ${configFile?.let(::display).orEmpty()}: ${plural(totals.errors, "error")}, ${plural(totals.warnings, "warning")}"
        if (totals.errors > 0) throw MojoFailureException("$summary. $hint")
        log.warn(summary)
    }

    protected fun reportUnused(unused: UnusedConfig, configFile: Path?) {
        if (unused.isEmpty) return
        val where = configFile?.let(::display) ?: "configuration"
        for ((kind, ids) in listOf("rules" to unused.rules, "preprocessors" to unused.preprocessors, "decorators" to unused.decorators)) {
            if (ids.isNotEmpty()) log.warn("Unused $kind found in $where: ${ids.joinToString(", ")}")
        }
        log.warn("Check the spelling of the configured ids.")
    }

    protected fun levelFor(totals: Totals): MavenJsLog.Level = when {
        totals.errors > 0 -> MavenJsLog.Level.ERROR
        totals.warnings > 0 -> MavenJsLog.Level.WARN
        else -> MavenJsLog.Level.INFO
    }

    protected fun plural(count: Int, noun: String): String = "$count $noun${if (count == 1) "" else "s"}"

    protected fun writeOutput(file: File, content: String): Path {
        val target = resolve(file)
        try {
            target.parent.createDirectories()
            target.writeText(content)
        } catch (e: IOException) {
            throw MojoExecutionException("Could not write output to $target: ${e.message}", e)
        }
        return target
    }

    /**
     * Prints formatted problems. Human-readable formats go through the Maven log; `github-actions` is written
     * to stdout unprefixed because GitHub only recognizes workflow commands at the start of a line.
     */
    protected fun printProblems(output: String, format: String, level: MavenJsLog.Level) {
        if (format == GITHUB_ACTIONS_FORMAT) {
            output.trimEnd().takeIf { it.isNotEmpty() }?.let(::println)
        } else {
            MavenJsLog.block(log, output, level)
        }
    }

    companion object {
        const val GITHUB_ACTIONS_FORMAT = "github-actions"

        /** Formats suitable for the build log (plus GitHub Actions annotations). */
        val CONSOLE_FORMATS: List<String> = listOf("stylish", "codeframe", "summary", "markdown", GITHUB_ACTIONS_FORMAT)

        /** All output formats of Redocly's `formatProblems`; the machine-readable ones are for report files. */
        val REPORT_FORMATS: List<String> = CONSOLE_FORMATS + listOf("json", "checkstyle", "codeclimate", "junit")

        val SEVERITIES: List<String> = listOf("warn", "error", "off")

        /** Output formats a bundled or joined description can be written in. */
        val EXTENSIONS: List<String> = listOf("yaml", "yml", "json")

        fun extension(file: File): String = file.extension.lowercase()
    }
}

class SkipParameter(val property: String, val enabled: Boolean)

/** Goals that read the Redocly configuration file. */
abstract class ConfiguredGoal<M : AbstractConfiguredMojo>(mojo: M) : Goal<M>(mojo) {

    protected val maxProblems: Int get() = mojo.maxProblems

    override fun validate() {
        super.validate()
        if (mojo.maxProblems <= 0) {
            throw MojoExecutionException("Invalid value '${mojo.maxProblems}' for openapi.maxProblems; must be greater than zero")
        }
    }

    /** The configuration file to use, or null when none is configured and `redocly.yaml` does not exist. */
    protected fun resolveConfigFile(): Path? {
        val explicit = mojo.configFile?.let(::resolve)
        if (explicit != null) {
            if (!Files.isRegularFile(explicit)) throw MojoExecutionException("Redocly configuration file not found: $explicit")
            return explicit
        }
        return basedir.resolve("redocly.yaml").takeIf { Files.isRegularFile(it) }
    }
}

/** Goals that process API descriptions selected via `apis`. */
abstract class ApiGoal<M : AbstractApiMojo>(mojo: M) : ConfiguredGoal<M>(mojo) {

    protected val lintConfig: String get() = mojo.lintConfig

    /** API entries as given by the user: aliases stay as-is, paths get forward slashes. */
    protected val jsApis: List<String> get() = mojo.apis.map(JsPaths::toJs)

    override fun validate() {
        super.validate()
        requireOneOf("openapi.lintConfig", mojo.lintConfig, SEVERITIES)
    }

    protected fun requireSingleApi(parameter: String, selected: Int) {
        if (selected > 1) {
            throw MojoExecutionException("$parameter can only be used with a single API, but $selected were selected; use <apis> to select one.")
        }
    }
}
