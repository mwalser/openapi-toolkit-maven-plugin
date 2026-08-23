package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.ScoreOptions
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File

/**
 * Scores OpenAPI 3 descriptions for integration simplicity and agent readiness (`redocly score`).
 * Processes every selected API (see `apis`); optionally fails the build when an agent-readiness score is
 * below `minScore`.
 */
@Mojo(name = "score", threadSafe = true)
class ScoreMojo : AbstractApiMojo() {

    /** Output format: `stylish` (default) or `json`. */
    @Parameter(property = "openapi.score.format", defaultValue = "stylish")
    var format: String = "stylish"

    /** Include per-operation details in the stylish output. */
    @Parameter(property = "openapi.score.operationDetails", defaultValue = "false")
    var operationDetails: Boolean = false

    /** When set, the score output is written to this file instead of the build log. Requires a single selected API. */
    @Parameter(property = "openapi.score.outputFile")
    var outputFile: File? = null

    /** Fail the build when the agent-readiness score (0-100) of any selected API is below this value. */
    @Parameter(property = "openapi.score.minScore")
    var minScore: Double? = null

    override fun validateParameters() {
        super.validateParameters()
        requireOneOf("openapi.score.format", format, listOf("stylish", "json"))
        minScore?.let {
            if (!it.isFinite() || it < 0.0 || it > 100.0) {
                throw MojoExecutionException("Invalid value '$it' for openapi.score.minScore; must be a number from 0 to 100")
            }
        }
    }

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun run() {
        val configPath = resolveConfigFile()
        val redocly = redocly()
        val options = ScoreOptions(
            cwd = jsCwd, configPath = configPath?.let { jsPath(it) }, apis = jsApis, format = format,
            operationDetails = operationDetails, lintConfig = lintConfig, maxProblems = maxProblems,
        )
        val result = redocly.score(options)
        reportConfigLint(result.configLint, configPath)

        val target = outputFile
        if (target != null) {
            requireSingleApi("openapi.score.outputFile", result.apis.size)
            val api = result.apis.single()
            val out = writeOutput(target, api.output)
            log.info("Score for ${relativize(api.path)} ($format) written to ${relativize(out.path)}")
        } else {
            for (api in result.apis) {
                log.info("Score for ${relativize(api.path)}:")
                MavenJsLog.block(log, api.output, MavenJsLog.Level.INFO)
            }
        }

        val threshold = minScore ?: return
        // the threshold needs the structured score; re-run in json format unless that is what was requested
        val scores = if (format == "json") result else redocly.score(options.copy(format = "json", lintConfig = "off"))
        val failing = scores.apis.filter { api ->
            val value = api.agentReadiness ?: throw MojoExecutionException("Could not read agentReadiness for ${relativize(api.path)} from the score output")
            value < threshold
        }
        if (failing.isNotEmpty()) {
            throw MojoFailureException(
                "Agent-readiness score below the required minimum of $threshold: " +
                    failing.joinToString { "${relativize(it.path)} (${it.agentReadiness})" },
            )
        }
        log.info("Agent-readiness ${if (scores.apis.size == 1) "score ${scores.apis.single().agentReadiness}" else "scores of ${scores.apis.size} APIs"} meet the required minimum of $threshold.")
    }
}
