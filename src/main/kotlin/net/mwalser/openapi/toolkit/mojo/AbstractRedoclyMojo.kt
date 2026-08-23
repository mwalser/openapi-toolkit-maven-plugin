package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.ConfigLintResult
import net.mwalser.openapi.toolkit.redocly.EngineMode
import net.mwalser.openapi.toolkit.redocly.JsPaths
import net.mwalser.openapi.toolkit.redocly.Redocly
import net.mwalser.openapi.toolkit.redocly.RedoclyException
import net.mwalser.openapi.toolkit.redocly.RedoclyRuntime
import net.mwalser.openapi.toolkit.redocly.Totals
import net.mwalser.openapi.toolkit.redocly.UnusedConfig
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File
import java.nio.file.Path

/**
 * Base of all goals: the runtime parameters every goal has, plus shared helpers.
 *
 * Parameters are declared at the level of the hierarchy where they actually take effect, so the generated
 * goal descriptors (`mvn help:describe`) only advertise parameters a goal honours:
 * - [AbstractRedoclyMojo]: `skip` — every goal
 * - [AbstractConfiguredMojo]: `configFile`, `maxProblems` — goals that read `redocly.yaml`
 * - [AbstractApiMojo]: `apis`, `lintConfig` — goals that process API descriptions
 * - `extends` only on `lint` and `bundle`, the goals that evaluate rules
 */
abstract class AbstractRedoclyMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    lateinit var project: MavenProject

    /** Skip execution of this goal. */
    @Parameter(property = "openapi.skip", defaultValue = "false")
    var skip: Boolean = false

    protected val basedir: Path get() = project.basedir.toPath().toAbsolutePath().normalize()

    /** The base directory as a JS path (what the JS side uses as `cwd`). */
    protected val jsCwd: String get() = JsPaths.toJs(basedir)

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    final override fun execute() {
        if (skip) {
            log.info("Skipping (openapi.skip=true)")
            return
        }
        validateParameters()
        try {
            run()
        } catch (e: RedoclyException) {
            if (log.isDebugEnabled && e.jsStack != null) log.debug(e.jsStack)
            throw MojoExecutionException(e.message, e)
        }
    }

    /**
     * Checks parameter values before anything is executed. Overrides must call `super.validateParameters()`.
     * Invalid values are reported as [MojoExecutionException] naming the property.
     */
    @Throws(MojoExecutionException::class)
    protected open fun validateParameters() {
    }

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    protected abstract fun run()

    // ---- parameter validation helpers -----------------------------------------------------------

    @Throws(MojoExecutionException::class)
    protected fun requireOneOf(property: String, value: String, allowed: Collection<String>) {
        if (value !in allowed) {
            throw MojoExecutionException("Invalid value '$value' for $property; expected one of: ${allowed.joinToString(", ")}")
        }
    }

    // ---- path helpers -----------------------------------------------------------------------------

    /** Resolves a possibly relative file against the base directory. */
    protected fun resolve(file: File): File = if (file.isAbsolute) file else File(project.basedir, file.path)

    /** Resolves a possibly relative file against the base directory and converts it to a JS path. */
    protected fun jsPath(file: File): String = JsPaths.toJs(resolve(file))

    protected fun jsPath(path: Path): String = JsPaths.toJs(path)

    /** Converts a path reported by the JS side back to a host path. */
    protected fun hostPath(jsPath: String): String = JsPaths.toHost(jsPath)

    /** Displays a path reported by the JS side relative to the base directory when possible. */
    protected fun relativize(jsPath: String): String {
        val host = hostPath(jsPath)
        val p = runCatching { Path.of(host) }.getOrNull() ?: return host
        return if (p.isAbsolute && p.startsWith(basedir)) basedir.relativize(p).toString() else host
    }

    // ---- Redocly runtime and reporting ----------------------------------------------------------

    protected fun redocly(): Redocly {
        // one runtime per JVM; the engine (interpreter, isolate, JIT) is chosen from what is on the classpath
        val runtime = RedoclyRuntime.shared(EngineMode.AUTO, MavenJsLog(log))
        return Redocly(runtime, MavenJsLog(log))
    }

    /** Prints the result of linting the configuration file and fails the build on configuration errors. */
    @Throws(MojoFailureException::class)
    protected fun reportConfigLint(result: ConfigLintResult?, configPath: Path?, format: String = "stylish") {
        if (result == null) return
        val t = result.totals
        if (t.errors == 0 && t.warnings == 0) return
        printProblems(result.output, format, if (t.errors > 0) MavenJsLog.Level.ERROR else MavenJsLog.Level.WARN)
        val summary = "Configuration file ${configPath ?: ""}: ${plural(t.errors, "error")}, ${plural(t.warnings, "warning")}"
        if (t.errors > 0) {
            throw MojoFailureException("$summary. Fix the configuration or set lintConfig=off.")
        }
        log.warn(summary)
    }

    protected fun reportUnused(unused: UnusedConfig, configPath: Path?) {
        if (unused.isEmpty) return
        val where = configPath?.toString() ?: "configuration"
        if (unused.rules.isNotEmpty()) log.warn("Unused rules found in $where: ${unused.rules.joinToString(", ")}")
        if (unused.preprocessors.isNotEmpty()) log.warn("Unused preprocessors found in $where: ${unused.preprocessors.joinToString(", ")}")
        if (unused.decorators.isNotEmpty()) log.warn("Unused decorators found in $where: ${unused.decorators.joinToString(", ")}")
        if (unused.rules.isNotEmpty() || unused.preprocessors.isNotEmpty()) log.warn("Check the spelling and verify the added plugin prefix.")
    }

    protected fun levelFor(totals: Totals): MavenJsLog.Level = when {
        totals.errors > 0 -> MavenJsLog.Level.ERROR
        totals.warnings > 0 -> MavenJsLog.Level.WARN
        else -> MavenJsLog.Level.INFO
    }

    protected fun plural(count: Int, noun: String): String = "$count $noun${if (count == 1) "" else "s"}"

    /** Writes text to a (possibly relative) file, creating parent directories. */
    protected fun writeOutput(file: File, content: String): File {
        val out = resolve(file)
        out.parentFile?.mkdirs()
        out.writeText(content)
        return out
    }

    /**
     * Prints formatted problems. Human-readable formats go through the Maven log; `github-actions` is written
     * to stdout unprefixed because GitHub only recognises workflow commands at the start of a line.
     */
    protected fun printProblems(output: String, format: String, level: MavenJsLog.Level) {
        if (format == GITHUB_ACTIONS_FORMAT) {
            output.trimEnd('\n', '\r').takeIf { it.isNotEmpty() }?.let { println(it) }
        } else {
            MavenJsLog.block(log, output, level)
        }
    }

    companion object {
        const val GITHUB_ACTIONS_FORMAT = "github-actions"

        /** Formats suitable for the build log (plus GitHub Actions annotations). */
        val CONSOLE_FORMATS: List<String> = listOf("stylish", "codeframe", "summary", "markdown", GITHUB_ACTIONS_FORMAT)

        /** All output formats understood by Redocly's `formatProblems`; the machine-readable ones are for report files. */
        val REPORT_FORMATS: List<String> = CONSOLE_FORMATS + listOf("json", "checkstyle", "codeclimate", "junit")

        val CONFIG_LINT_SEVERITIES: List<String> = listOf("warn", "error", "off")
    }
}

/** Goals that read the Redocly configuration file. */
abstract class AbstractConfiguredMojo : AbstractRedoclyMojo() {

    /**
     * The Redocly configuration file. Defaults to `redocly.yaml` in the project base directory when that
     * file exists; without a configuration file Redocly's built-in `recommended` ruleset is used.
     */
    @Parameter(property = "openapi.configFile")
    var configFile: File? = null

    /** Maximum number of problems to print (per API description and for the configuration file). */
    @Parameter(property = "openapi.maxProblems", defaultValue = "100")
    var maxProblems: Int = 100

    override fun validateParameters() {
        super.validateParameters()
        if (maxProblems <= 0) throw MojoExecutionException("Invalid value '$maxProblems' for openapi.maxProblems; must be greater than zero")
    }

    /** The configuration file to use, or null when none is configured and `redocly.yaml` does not exist. */
    @Throws(MojoExecutionException::class)
    protected fun resolveConfigFile(): Path? {
        val explicit = configFile
        if (explicit != null) {
            val path = resolve(explicit).toPath()
            if (!path.toFile().isFile) {
                throw MojoExecutionException("Redocly configuration file not found: $path")
            }
            return path.normalize()
        }
        return basedir.resolve("redocly.yaml").takeIf { it.toFile().isFile }
    }
}

/** Goals that process API descriptions selected via `apis`. */
abstract class AbstractApiMojo : AbstractConfiguredMojo() {

    /**
     * The API descriptions to process, each either an alias from the `apis` section of the configuration
     * file or a path (relative to the project base directory) or URL. When empty, all APIs defined in the
     * configuration file are processed.
     */
    @Parameter(property = "openapi.apis")
    var apis: List<String> = emptyList()

    /** Severity used when linting the configuration file itself before the command runs: `warn`, `error` or `off`. */
    @Parameter(property = "openapi.lintConfig", defaultValue = "warn")
    var lintConfig: String = "warn"

    /** API entries as given by the user: aliases stay as-is, paths get forward slashes. */
    protected val jsApis: List<String> get() = apis.map { JsPaths.toJs(it) }

    override fun validateParameters() {
        super.validateParameters()
        requireOneOf("openapi.lintConfig", lintConfig, CONFIG_LINT_SEVERITIES)
    }

    /** Fails when a single-output parameter is combined with more than one selected API. */
    @Throws(MojoExecutionException::class)
    protected fun requireSingleApi(parameter: String, selected: Int) {
        if (selected > 1) {
            throw MojoExecutionException("$parameter can only be used with a single API, but $selected were selected; use <apis> to select one.")
        }
    }
}
