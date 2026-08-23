package net.mwalser.openapi.toolkit.mojo

import org.apache.maven.plugin.MojoExecutionException
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Parameter validation and the parameters each goal advertises in the generated plugin descriptor. */
class MojoParametersTest {

    private fun assertRejected(property: String, mojo: AbstractRedoclyMojo) {
        val error = assertFailsWith<MojoExecutionException>("$property should be rejected") { mojo.execute() }
        assertContains(error.message!!, property)
    }

    @Test
    fun `invalid enumerated values are rejected before anything runs`() {
        assertRejected("openapi.lint.format", LintMojo().apply { format = "typo" })
        assertRejected("openapi.lint.reportFormat", LintMojo().apply { reportFile = java.io.File("x"); reportFormat = "typo" })
        assertRejected("openapi.lintConfig", LintMojo().apply { lintConfig = "loud" })
        assertRejected("openapi.maxProblems", LintMojo().apply { maxProblems = 0 })
        assertRejected("openapi.bundle.ext", BundleMojo().apply { ext = "xml" })
        assertRejected("openapi.bundle.componentRenamingConflicts", BundleMojo().apply { componentRenamingConflicts = "fatal" })
        assertRejected("openapi.bundle.classifier", BundleMojo().apply { attach = true; classifier = " " })
        assertRejected("openapi.checkConfig.severity", CheckConfigMojo().apply { severity = "off" })
        assertRejected("openapi.checkConfig.format", CheckConfigMojo().apply { format = "typo" })
        assertRejected("openapi.stats.format", StatsMojo().apply { format = "xml" })
        assertRejected("openapi.score.format", ScoreMojo().apply { format = "markdown" })
        assertRejected("openapi.score.minScore", ScoreMojo().apply { minScore = Double.NaN })
        assertRejected("openapi.score.minScore", ScoreMojo().apply { minScore = 101.0 })
        assertRejected("openapi.split.separator", SplitMojo().apply { separator = " " })
    }

    @Test
    fun `reportFormat is only validated when a report is written`() {
        // invalid reportFormat without reportFile is irrelevant and must not block the build
        val mojo = LintMojo().apply { reportFormat = "typo"; skip = true }
        mojo.execute()
    }

    @Test
    fun `goals advertise exactly the parameters they honour`() {
        val descriptorFile = Path.of("target/classes/META-INF/maven/plugin.xml")
        assumeTrue(Files.exists(descriptorFile), "plugin descriptor not generated (run via Maven)")
        val descriptor = descriptorFile.readText()
        fun parameters(goal: String): Set<String> {
            val mojo = Regex("<goal>$goal</goal>.*?</mojo>", RegexOption.DOT_MATCHES_ALL).find(descriptor)!!.value
            return Regex("<name>([^<]+)</name>").findAll(mojo).map { it.groupValues[1] }.toSet() - "project"
        }
        val runtime = setOf("skip")
        val configured = runtime + setOf("configFile", "maxProblems")
        val api = configured + setOf("apis", "lintConfig")

        assertEquals(api + setOf("extends", "format", "reportFile", "reportFormat", "failOnErrors", "failOnWarnings", "skipRules", "skipPreprocessors", "generateIgnoreFile"), parameters("lint"))
        assertEquals(
            api + setOf("extends", "outputDirectory", "outputFile", "ext", "dereferenced", "force", "removeUnusedComponents", "keepUrlReferences", "componentNamesStrategy", "componentRenamingConflicts", "skipDecorators", "skipPreprocessors", "addResource", "attach", "classifier"),
            parameters("bundle"),
        )
        assertEquals(configured + setOf("severity", "format"), parameters("check-config"))
        assertEquals(api + setOf("format", "outputFile"), parameters("stats"))
        assertEquals(api + setOf("format", "operationDetails", "outputFile", "minScore"), parameters("score"))
        assertEquals(api + setOf("outputFile", "prefixTagsWithInfoProp", "prefixTagsWithFilename", "prefixComponentsWithInfoProp", "withoutXTagGroups"), parameters("join"))
        assertEquals(runtime + setOf("api", "outputDirectory", "separator"), parameters("split"))
    }
}
