package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.CheckConfigOptions
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter

/** Lints the Redocly configuration file (`redocly check-config`). */
@Mojo(name = "check-config", threadSafe = true)
class CheckConfigMojo : AbstractConfiguredMojo() {

    /** Severity of configuration problems: `warn` (default) or `error`. */
    @Parameter(property = "openapi.checkConfig.severity", defaultValue = "warn")
    var severity: String = "warn"

    /** Build log output format (see the `lint` goal). */
    @Parameter(property = "openapi.checkConfig.format", defaultValue = "stylish")
    var format: String = "stylish"

    override fun validateParameters() {
        super.validateParameters()
        requireOneOf("openapi.checkConfig.severity", severity, listOf("warn", "error"))
        requireOneOf("openapi.checkConfig.format", format, CONSOLE_FORMATS)
    }

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun run() {
        val configPath = resolveConfigFile()
        if (configPath == null) {
            log.warn("No Redocly configuration file found (redocly.yaml); nothing to check.")
            return
        }
        val result = redocly().checkConfig(
            CheckConfigOptions(cwd = jsCwd, configPath = jsPath(configPath), severity = severity, format = format, maxProblems = maxProblems),
        )
        reportConfigLint(result.configLint, configPath, format)
        val t = result.configLint?.totals
        if (t == null || (t.errors == 0 && t.warnings == 0)) {
            log.info("Configuration file ${relativize(configPath.toString())} is valid.")
        }
    }
}
