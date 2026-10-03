package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.ApiScoreResult
import net.mwalser.openapi.toolkit.redocly.ScoreOptions
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException

internal class ScoreGoal(mojo: ScoreMojo) : ApiGoal<ScoreMojo>(mojo) {

    override val skipGoal get() = SkipParameter("openapi.toolkit.score.skip", mojo.skipScore)

    override fun validate() {
        super.validate()
        requireOneOf("openapi.toolkit.score.format", mojo.format, listOf("stylish", "json"))
        mojo.minScore?.let { minScore ->
            if (minScore !in 0.0..100.0) throw MojoExecutionException("Invalid value '$minScore' for openapi.toolkit.score.minScore; must be a number from 0 to 100")
        }
        if (mojo.outputFile != null) requireSingleApi("openapi.toolkit.score.outputFile", mojo.apis.size)
    }

    override fun run() {
        val configFile = resolveConfigFile()
        val result = redocly().score(
            ScoreOptions(
                cwd = jsCwd,
                configPath = configFile?.let(::jsPath),
                apis = jsApis,
                format = mojo.format,
                operationDetails = mojo.operationDetails,
                lintConfig = lintConfig,
                maxProblems = maxProblems,
            ),
        )
        reportConfigLint(result.configLint, configFile)

        val outputFile = mojo.outputFile
        if (outputFile != null) {
            requireSingleApi("openapi.toolkit.score.outputFile", result.apis.size)
            val api = result.apis.single()
            val written = writeOutput(outputFile, api.output)
            log.info("Score for ${display(api.path)} (${mojo.format}) written to ${display(written)}")
        } else {
            for (api in result.apis) {
                log.info("Score for ${display(api.path)}:")
                MavenJsLog.block(log, api.output, MavenJsLog.Level.INFO)
            }
        }
        mojo.minScore?.let { enforceMinScore(it, result.apis) }
    }

    private fun enforceMinScore(minScore: Double, apis: List<ApiScoreResult>) {
        val failing = apis.filter { it.agentReadiness < minScore }
        if (failing.isNotEmpty()) {
            val failures = failing.joinToString(", ") { "${display(it.path)} (${score(it.agentReadiness)})" }
            throw MojoFailureException("Agent-readiness score below the required minimum of ${score(minScore)}: $failures")
        }
        val scores = apis.joinToString(", ") { score(it.agentReadiness) }
        val summary = if (apis.size == 1) "score $scores meets" else "scores $scores meet"
        log.info("Agent-readiness $summary the required minimum of ${score(minScore)}.")
    }

    /** Whole scores are the common case; `85` reads better than `85.0`. */
    private fun score(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
