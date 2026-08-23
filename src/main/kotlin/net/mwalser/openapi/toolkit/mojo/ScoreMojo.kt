package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.JsPaths
import net.mwalser.openapi.toolkit.redocly.ScoreOptions
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File

/**
 * Scores an OpenAPI 3 description for integration simplicity and agent readiness (`redocly score`).
 * Optionally fails the build when the agent-readiness score is below `minScore`.
 */
@Mojo(name = "score", threadSafe = true)
class ScoreMojo : AbstractRedoclyMojo() {

    /** The API to score: an alias or a path. Defaults to the first API of the configuration file. */
    @Parameter(property = "openapi.score.api")
    var api: String? = null

    /** Output format: `stylish` (default) or `json`. */
    @Parameter(property = "openapi.score.format", defaultValue = "stylish")
    var format: String = "stylish"

    /** Include per-operation details in the stylish output. */
    @Parameter(property = "openapi.score.operationDetails", defaultValue = "false")
    var operationDetails: Boolean = false

    /** When set, the score output is written to this file instead of the build log. */
    @Parameter(property = "openapi.score.outputFile")
    var outputFile: File? = null

    /** Fail the build when the agent-readiness score (0-100) is below this value. */
    @Parameter(property = "openapi.score.minScore")
    var minScore: Double? = null

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun run() {
        val configPath = resolveConfigFile()
        val redocly = redocly()
        val result = redocly.score(
            ScoreOptions(cwd = jsCwd, configPath = configPath?.let { jsPath(it) }, api = api?.let { JsPaths.toJs(it) }, format = format, operationDetails = operationDetails, lintConfig = lintConfig),
        )
        reportConfigLint(result.configLint, configPath)
        val target = outputFile
        if (target != null) {
            val out = if (target.isAbsolute) target else File(project.basedir, target.path)
            out.parentFile?.mkdirs()
            out.writeText(result.output)
            log.info("Score for ${relativize(result.path)} ($format) written to ${relativize(out.path)}")
        } else {
            log.info("Score for ${relativize(result.path)}:")
            MavenJsLog.block(log, result.output, MavenJsLog.Level.INFO)
        }

        val threshold = minScore ?: return
        val json = if (result.score != null) result else redocly.score(
            ScoreOptions(cwd = jsCwd, configPath = configPath?.let { jsPath(it) }, api = api?.let { JsPaths.toJs(it) }, format = "json", lintConfig = "off"),
        )
        val agentReadiness = (json.score?.get("agentReadiness") as? Number)?.toDouble()
            ?: throw MojoExecutionException("Could not read agentReadiness from the score output")
        if (agentReadiness < threshold) {
            throw MojoFailureException("Agent-readiness score $agentReadiness is below the required minimum of $threshold.")
        }
        log.info("Agent-readiness score $agentReadiness meets the required minimum of $threshold.")
    }
}
