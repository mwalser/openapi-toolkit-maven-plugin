package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.JsPaths
import net.mwalser.openapi.toolkit.redocly.StatsOptions
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File

/** Prints statistics about an API description (`redocly stats`): number of paths, operations, schemas, ... */
@Mojo(name = "stats", threadSafe = true)
class StatsMojo : AbstractRedoclyMojo() {

    /** The API to analyse: an alias or a path. Defaults to the first API of the configuration file. */
    @Parameter(property = "openapi.stats.api")
    var api: String? = null

    /** Output format: `stylish` (default), `json` or `markdown`. */
    @Parameter(property = "openapi.stats.format", defaultValue = "stylish")
    var format: String = "stylish"

    /** When set, the statistics are written to this file instead of the build log. */
    @Parameter(property = "openapi.stats.outputFile")
    var outputFile: File? = null

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun run() {
        val configPath = resolveConfigFile()
        val result = redocly().stats(
            StatsOptions(cwd = jsCwd, configPath = configPath?.let { jsPath(it) }, api = api?.let { JsPaths.toJs(it) }, format = format, lintConfig = lintConfig),
        )
        reportConfigLint(result.configLint, configPath)
        val target = outputFile
        if (target != null) {
            val out = if (target.isAbsolute) target else File(project.basedir, target.path)
            out.parentFile?.mkdirs()
            out.writeText(result.output)
            log.info("Statistics for ${relativize(result.path)} ($format) written to ${relativize(out.path)}")
        } else {
            log.info("Statistics for ${relativize(result.path)}:")
            MavenJsLog.block(log, result.output, MavenJsLog.Level.INFO)
        }
    }
}
