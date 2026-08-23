package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.StatsOptions

internal class StatsGoal(mojo: StatsMojo) : ApiGoal<StatsMojo>(mojo) {

    override fun validate() {
        super.validate()
        requireOneOf("openapi.stats.format", mojo.format, listOf("stylish", "json", "markdown"))
    }

    override fun run() {
        val configFile = resolveConfigFile()
        val result = redocly().stats(
            StatsOptions(cwd = jsCwd, configPath = configFile?.let(::jsPath), apis = jsApis, format = mojo.format, lintConfig = lintConfig, maxProblems = maxProblems),
        )
        reportConfigLint(result.configLint, configFile)

        val outputFile = mojo.outputFile
        if (outputFile != null) {
            requireSingleApi("openapi.stats.outputFile", result.apis.size)
            val api = result.apis.single()
            val written = writeOutput(outputFile, api.output)
            log.info("Statistics for ${display(api.path)} (${mojo.format}) written to ${display(written)}")
            return
        }
        for (api in result.apis) {
            log.info("Statistics for ${display(api.path)}:")
            MavenJsLog.block(log, api.output, MavenJsLog.Level.INFO)
        }
    }
}
