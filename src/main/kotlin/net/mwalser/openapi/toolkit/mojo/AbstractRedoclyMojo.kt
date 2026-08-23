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

/** Parameters and helpers shared by all Redocly-backed goals. */
abstract class AbstractRedoclyMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    lateinit var project: MavenProject

    /** Skip execution of this goal. */
    @Parameter(property = "openapi.skip", defaultValue = "false")
    var skip: Boolean = false

    /**
     * The Redocly configuration file. Defaults to `redocly.yaml` in the project base directory when that
     * file exists; without a configuration file Redocly's built-in `recommended` ruleset is used.
     */
    @Parameter(property = "openapi.configFile")
    var configFile: File? = null

    /**
     * The API descriptions to process, each either an alias from the `apis` section of the configuration
     * file or a path (relative to the project base directory) or URL. When empty, all APIs defined in the
     * configuration file are processed.
     */
    @Parameter(property = "openapi.apis")
    var apis: List<String> = emptyList()

    /** Overrides the `extends` list of the configuration, e.g. `recommended`, `minimal`, `recommended-strict`. */
    @Parameter(property = "openapi.extends")
    var extends: List<String>? = null

    /**
     * How the embedded JavaScript engine runs: `AUTO` (default), `INTERPRETER` or `ISOLATE`.
     * See the plugin documentation for the performance trade-offs.
     */
    @Parameter(property = "openapi.engine", defaultValue = "AUTO")
    var engine: EngineMode = EngineMode.AUTO

    /** Severity used when linting the configuration file itself before the command runs: `warn`, `error` or `off`. */
    @Parameter(property = "openapi.lintConfig", defaultValue = "warn")
    var lintConfig: String = "warn"

    /** Maximum number of problems to print per API description. */
    @Parameter(property = "openapi.maxProblems", defaultValue = "100")
    var maxProblems: Int = 100

    protected val basedir: Path get() = project.basedir.toPath().toAbsolutePath().normalize()

    /** The base directory as a JS path (what the JS side uses as `cwd`). */
    protected val jsCwd: String get() = JsPaths.toJs(basedir)

    /** Resolves a possibly relative file against the base directory and converts it to a JS path. */
    protected fun jsPath(file: File): String = JsPaths.toJs(if (file.isAbsolute) file else File(project.basedir, file.path))

    protected fun jsPath(path: Path): String = JsPaths.toJs(path)

    /** API entries as given by the user: aliases stay as-is, paths get forward slashes. */
    protected val jsApis: List<String> get() = apis.map { JsPaths.toJs(it) }

    /** Converts a path reported by the JS side back to a host path for display. */
    protected fun hostPath(jsPath: String): String = JsPaths.toHost(jsPath)

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    final override fun execute() {
        if (skip) {
            log.info("Skipping (openapi.skip=true)")
            return
        }
        try {
            run()
        } catch (e: RedoclyException) {
            if (log.isDebugEnabled && e.jsStack != null) log.debug(e.jsStack)
            throw MojoExecutionException(e.message, e)
        }
    }

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    protected abstract fun run()

    /** The configuration file to use, or null when none is configured and `redocly.yaml` does not exist. */
    protected fun resolveConfigFile(): Path? {
        val explicit = configFile
        if (explicit != null) {
            val path = if (explicit.isAbsolute) explicit.toPath() else basedir.resolve(explicit.toPath())
            if (!path.toFile().isFile) {
                throw MojoExecutionException("Redocly configuration file not found: $path")
            }
            return path.normalize()
        }
        return basedir.resolve("redocly.yaml").takeIf { it.toFile().isFile }
    }

    protected fun redocly(): Redocly {
        val runtime = RedoclyRuntime.shared(engine, MavenJsLog(log))
        log.debug("Redocly ${runtime.redoclyVersion} (engine: ${runtime.effectiveEngine})")
        return Redocly(runtime, MavenJsLog(log))
    }

    /** Prints the result of linting the configuration file and fails the build on configuration errors. */
    @Throws(MojoFailureException::class)
    protected fun reportConfigLint(result: ConfigLintResult?, configPath: Path?) {
        if (result == null) return
        val t = result.totals
        if (t.errors == 0 && t.warnings == 0) return
        MavenJsLog.block(log, result.output, if (t.errors > 0) MavenJsLog.Level.ERROR else MavenJsLog.Level.WARN)
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

    /** Displays a path reported by the JS side relative to the base directory when possible. */
    protected fun relativize(jsPath: String): String {
        val host = hostPath(jsPath)
        val p = runCatching { Path.of(host) }.getOrNull() ?: return host
        return if (p.isAbsolute && p.startsWith(basedir)) basedir.relativize(p).toString() else host
    }
}
