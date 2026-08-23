package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.CheckConfigOptions

internal class CheckConfigGoal(mojo: CheckConfigMojo) : ConfiguredGoal<CheckConfigMojo>(mojo) {

    override fun validate() {
        super.validate()
        requireOneOf("openapi.checkConfig.severity", mojo.severity, listOf("warn", "error"))
        requireOneOf("openapi.checkConfig.format", mojo.format, CONSOLE_FORMATS)
    }

    override fun run() {
        val configFile = resolveConfigFile()
        if (configFile == null) {
            log.warn("No Redocly configuration file found (redocly.yaml); nothing to check.")
            return
        }
        val result = redocly().checkConfig(
            CheckConfigOptions(cwd = jsCwd, configPath = jsPath(configFile), severity = mojo.severity, format = mojo.format, maxProblems = maxProblems),
        )
        reportConfigLint(result.configLint, configFile, mojo.format, hint = "Fix the configuration.")
        if (result.configLint?.totals?.hasProblems != true) log.info("Configuration file ${display(configFile)} is valid.")
    }
}
