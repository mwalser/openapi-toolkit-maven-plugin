package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.StatsOptions
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File

/**
 * Prints statistics about API descriptions (`redocly stats`): number of paths, operations, schemas, ...
 * Processes every selected API (see `apis`).
 */
@Mojo(name = "stats", threadSafe = true)
class StatsMojo : AbstractApiMojo() {

    /** Output format: `stylish` (default), `json` or `markdown`. */
    @Parameter(property = "openapi.stats.format", defaultValue = "stylish")
    var format: String = "stylish"

    /** When set, the statistics are written to this file instead of the build log. Requires a single selected API. */
    @Parameter(property = "openapi.stats.outputFile")
    var outputFile: File? = null

    override fun validateParameters() {
        super.validateParameters()
        requireOneOf("openapi.stats.format", format, listOf("stylish", "json", "markdown"))
    }

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun run() {
        val configPath = resolveConfigFile()
        val result = redocly().stats(
            StatsOptions(cwd = jsCwd, configPath = configPath?.let { jsPath(it) }, apis = jsApis, format = format, lintConfig = lintConfig, maxProblems = maxProblems),
        )
        reportConfigLint(result.configLint, configPath)
        val target = outputFile
        if (target != null) {
            requireSingleApi("openapi.stats.outputFile", result.apis.size)
            val api = result.apis.single()
            val out = writeOutput(target, api.output)
            log.info("Statistics for ${relativize(api.path)} ($format) written to ${relativize(out.path)}")
            return
        }
        for (api in result.apis) {
            log.info("Statistics for ${relativize(api.path)}:")
            MavenJsLog.block(log, api.output, MavenJsLog.Level.INFO)
        }
    }
}
