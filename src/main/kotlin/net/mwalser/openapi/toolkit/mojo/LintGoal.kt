package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.LintOptions
import org.apache.maven.plugin.MojoFailureException

internal class LintGoal(mojo: LintMojo) : ApiGoal<LintMojo>(mojo) {

    override val skipGoal get() = SkipParameter("openapi.toolkit.lint.skip", mojo.skipLint)

    override fun validate() {
        super.validate()
        requireOneOf("openapi.toolkit.lint.format", mojo.format, CONSOLE_FORMATS)
        if (mojo.reportFile != null) requireOneOf("openapi.toolkit.lint.reportFormat", mojo.reportFormat, REPORT_FORMATS)
    }

    override fun run() {
        val configFile = resolveConfigFile()
        val result = redocly().lint(
            LintOptions(
                cwd = jsCwd,
                configPath = configFile?.let(::jsPath),
                apis = jsApis,
                extends = mojo.extendsRulesets,
                format = mojo.format,
                reportFormat = if (mojo.reportFile != null) mojo.reportFormat else null,
                maxProblems = maxProblems,
                skipRules = mojo.skipRules,
                generateIgnoreFile = mojo.generateIgnoreFile,
                lintConfig = lintConfig,
            ),
        )

        reportConfigLint(result.configLint, configFile, mojo.format)
        if (result.usedDefaultConfig) log.info("No Redocly configuration found - using the built-in 'recommended' ruleset.")
        for (api in result.apis) {
            val using = api.alias?.let { " using lint rules for api '$it'" }.orEmpty()
            log.info("Validating ${display(api.path)}$using (${api.durationMillis} ms)")
            if (!mojo.generateIgnoreFile) printProblems(api.output, mojo.format, levelFor(api.totals))
        }
        mojo.reportFile?.let { reportFile ->
            val written = writeOutput(reportFile, result.report.orEmpty())
            log.info("Lint report (${mojo.reportFormat}) written to ${display(written)}")
        }
        result.ignoreFile?.let { log.info("Explicitly ignored ${plural(it.ignored, "problem")} via .redocly.lint-ignore.yaml") }
        reportUnused(result.unused, configFile)

        val totals = result.totals
        val ignored = if (totals.ignored > 0) ", ${plural(totals.ignored, "problem")} explicitly ignored" else ""
        val summary = "${plural(result.apis.size, "API description")} validated: ${plural(totals.errors, "error")}, ${plural(totals.warnings, "warning")}$ignored"
        when {
            mojo.generateIgnoreFile -> log.info(summary)
            totals.errors > 0 && mojo.failOnErrors -> {
                log.error(summary)
                throw MojoFailureException("OpenAPI lint failed with ${plural(totals.errors, "error")}.")
            }
            totals.warnings > 0 && mojo.failOnWarnings -> {
                log.error(summary)
                throw MojoFailureException("OpenAPI lint failed with ${plural(totals.warnings, "warning")} (failOnWarnings=true).")
            }
            totals.hasProblems -> log.warn(summary)
            else -> log.info(summary)
        }
    }
}
