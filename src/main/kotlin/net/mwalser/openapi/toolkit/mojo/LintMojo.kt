package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.LintOptions
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File

/**
 * Lints API descriptions with Redocly (`redocly lint`). Fails the build when problems with severity
 * `error` are found (configurable via `failOnErrors` / `failOnWarnings`).
 */
@Mojo(name = "lint", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
class LintMojo : AbstractRedoclyMojo() {

    /**
     * Console output format: `stylish` (default), `codeframe`, `summary`, `markdown`, `github-actions`,
     * `json`, `checkstyle`, `codeclimate` or `junit`.
     */
    @Parameter(property = "openapi.lint.format", defaultValue = "stylish")
    var format: String = "stylish"

    /** When set, all problems are additionally written to this file in `reportFormat`. */
    @Parameter(property = "openapi.lint.reportFile")
    var reportFile: File? = null

    /** Format of `reportFile`: `checkstyle` (default), `junit`, `json`, `codeclimate`, `markdown`, `summary`, `stylish` or `codeframe`. */
    @Parameter(property = "openapi.lint.reportFormat", defaultValue = "checkstyle")
    var reportFormat: String = "checkstyle"

    /** Fail the build when problems with severity `error` are found. */
    @Parameter(property = "openapi.lint.failOnErrors", defaultValue = "true")
    var failOnErrors: Boolean = true

    /** Fail the build when problems with severity `warn` are found. */
    @Parameter(property = "openapi.lint.failOnWarnings", defaultValue = "false")
    var failOnWarnings: Boolean = false

    /** Rule ids to skip. */
    @Parameter(property = "openapi.lint.skipRules")
    var skipRules: List<String>? = null

    /** Preprocessor ids to skip. */
    @Parameter(property = "openapi.lint.skipPreprocessors")
    var skipPreprocessors: List<String>? = null

    /**
     * Instead of reporting, write all found problems to `.redocly.lint-ignore.yaml` next to the
     * configuration file so they are ignored from now on (`redocly lint --generate-ignore-file`).
     */
    @Parameter(property = "openapi.lint.generateIgnoreFile", defaultValue = "false")
    var generateIgnoreFile: Boolean = false

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun run() {
        val configPath = resolveConfigFile()
        val redocly = redocly()
        val result = redocly.lint(
            LintOptions(
                cwd = jsCwd,
                configPath = configPath?.let { jsPath(it) },
                apis = jsApis,
                extends = extends,
                format = format,
                reportFormat = if (reportFile != null) reportFormat else null,
                maxProblems = maxProblems,
                skipRules = skipRules,
                skipPreprocessors = skipPreprocessors,
                generateIgnoreFile = generateIgnoreFile,
                lintConfig = lintConfig,
            ),
        )

        reportConfigLint(result.configLint, configPath)
        if (result.usedDefaultConfig) {
            log.info("No Redocly configuration found - using the built-in 'recommended' ruleset.")
        }

        for (api in result.apis) {
            val using = api.alias?.let { " using lint rules for api '$it'" } ?: ""
            log.info("Validating ${relativize(api.path)}$using (${api.durationMillis} ms)")
            if (!generateIgnoreFile) {
                MavenJsLog.block(log, api.output, levelFor(api.totals))
            }
        }

        reportFile?.let { file ->
            val out = if (file.isAbsolute) file else File(project.basedir, file.path)
            out.parentFile?.mkdirs()
            out.writeText(result.report ?: "")
            log.info("Lint report ($reportFormat) written to ${relativize(out.path)}")
        }

        result.ignoreFile?.let {
            log.info("Explicitly ignored ${plural(it.ignored, "problem")} via .redocly.lint-ignore.yaml")
        }
        reportUnused(result.unused, configPath)

        val t = result.totals
        val summary = "${plural(result.apis.size, "API description")} validated: ${plural(t.errors, "error")}, ${plural(t.warnings, "warning")}" +
            (if (t.ignored > 0) ", ${plural(t.ignored, "problem")} explicitly ignored" else "")
        if (generateIgnoreFile) {
            log.info(summary)
            return
        }
        when {
            t.errors > 0 && failOnErrors -> {
                log.error(summary)
                throw MojoFailureException("OpenAPI lint failed with ${plural(t.errors, "error")}.")
            }
            t.warnings > 0 && failOnWarnings -> {
                log.error(summary)
                throw MojoFailureException("OpenAPI lint failed with ${plural(t.warnings, "warning")} (failOnWarnings=true).")
            }
            t.errors > 0 || t.warnings > 0 -> log.warn(summary)
            else -> log.info(summary)
        }
    }
}
