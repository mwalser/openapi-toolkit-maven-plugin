package net.mwalser.openapi.toolkit.mojo

import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.project.MavenProject
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Parameter validation and the parameters each goal advertises in the generated plugin descriptor. */
class MojoParametersTest {

    private class OutputGoal(mojo: LintMojo) : Goal<LintMojo>(mojo) {
        override val skipGoal get() = SkipParameter("openapi.lint.skip", mojo.skipLint)
        override fun run() {}
        fun write(file: File) = writeOutput(file, "content")
    }

    /** A JoinMojo as Maven would configure it: `outputFile` has a default value and is never null. */
    private fun join() = JoinMojo().apply { outputFile = File("joined.yaml") }

    private fun assertRejected(property: String, mojo: AbstractRedoclyMojo) {
        val error = assertFailsWith<MojoExecutionException>("$property should be rejected") { mojo.execute() }
        assertContains(error.message!!, property)
    }

    @Test
    fun `invalid enumerated values are rejected before anything runs`() {
        assertRejected("openapi.lint.format", LintMojo().apply { format = "typo" })
        assertRejected("openapi.lint.format", LintMojo().apply { format = "junit" }) // machine formats are for reportFile
        assertRejected("openapi.lint.reportFormat", LintMojo().apply { reportFile = File("x"); reportFormat = "typo" })
        assertRejected("openapi.lintConfig", LintMojo().apply { lintConfig = "loud" })
        assertRejected("openapi.maxProblems", LintMojo().apply { maxProblems = 0 })
        assertRejected("openapi.bundle.ext", BundleMojo().apply { ext = "xml" })
        assertRejected("openapi.bundle.ext", BundleMojo().apply { ext = "yaml"; outputFile = File("spec.json") })
        assertRejected("openapi.bundle.componentNamesStrategy", BundleMojo().apply { componentNamesStrategy = "auto" })
        assertRejected("openapi.bundle.componentRenamingConflicts", BundleMojo().apply { componentRenamingConflicts = "fatal" })
        assertRejected("openapi.bundle.classifier", BundleMojo().apply { attach = true; classifier = " " })
        assertRejected("openapi.checkConfig.severity", CheckConfigMojo().apply { severity = "off" })
        assertRejected("openapi.checkConfig.format", CheckConfigMojo().apply { format = "typo" })
        assertRejected("openapi.stats.format", StatsMojo().apply { format = "xml" })
        assertRejected("openapi.score.format", ScoreMojo().apply { format = "markdown" })
        assertRejected("openapi.score.minScore", ScoreMojo().apply { minScore = Double.NaN })
        assertRejected("openapi.score.minScore", ScoreMojo().apply { minScore = 101.0 })
        assertRejected("openapi.split.separator", SplitMojo().apply { separator = " " })
        assertRejected("openapi.join.prefixTagsWithFilename", join().apply { prefixTagsWithInfoProp = "title"; prefixTagsWithFilename = true })
        assertRejected("openapi.join.withoutXTagGroups", join().apply { prefixTagsWithInfoProp = "title"; withoutXTagGroups = true })
        assertRejected("openapi.join.withoutXTagGroups", join().apply { prefixTagsWithFilename = true; withoutXTagGroups = true })
    }

    @Test
    fun `reportFormat is only validated when a report is written`() {
        // invalid reportFormat without reportFile is irrelevant and must not block the build
        val mojo = LintMojo().apply { reportFormat = "typo"; skip = true }
        mojo.execute()
    }

    @Test
    fun `the goal-specific skip parameter skips before validation`() {
        LintMojo().apply { format = "typo"; skipLint = true }.execute()
        BundleMojo().apply { ext = "xml"; skipBundle = true }.execute()
    }

    @Test
    fun `output io failures become actionable mojo errors`(@TempDir dir: Path) {
        val target = dir.resolve("existing-directory")
        Files.createDirectory(target)
        val mojo = LintMojo().apply { project = MavenProject().also { it.file = dir.resolve("pom.xml").toFile() } }
        val error = assertFailsWith<MojoExecutionException> { OutputGoal(mojo).write(target.toFile()) }
        assertContains(error.message.orEmpty(), "Could not write output to")
        assertContains(error.message.orEmpty(), target.toString())
    }

    @Test
    fun `goals advertise exactly the parameters they honor`() {
        val descriptorFile = Path.of("target/classes/META-INF/maven/plugin.xml")
        assumeTrue(Files.exists(descriptorFile), "plugin descriptor not generated (run via Maven)")
        val descriptor = descriptorFile.readText()
        val parameterTag = Regex("<parameter>(.*?)</parameter>", RegexOption.DOT_MATCHES_ALL)
        fun mojoXml(goal: String): String =
            Regex("<goal>$goal</goal>.*?</mojo>", RegexOption.DOT_MATCHES_ALL).find(descriptor)!!.value
        fun parameters(goal: String): Set<String> =
            parameterTag.findAll(mojoXml(goal)).map {
                val parameter = it.groupValues[1]
                Regex("<alias>([^<]+)</alias>").find(parameter)?.groupValues?.get(1)
                    ?: Regex("<name>([^<]+)</name>").find(parameter)!!.groupValues[1]
            }.toSet() - setOf("project", "session")

        val runtime = setOf("skip")
        val configured = runtime + setOf("configFile", "maxProblems")
        val api = configured + setOf("apis", "lintConfig")

        assertEquals(api + setOf("skipLint", "extends", "format", "reportFile", "reportFormat", "failOnErrors", "failOnWarnings", "skipRules", "generateIgnoreFile"), parameters("lint"))
        assertEquals(
            api + setOf("skipBundle", "extends", "outputDirectory", "outputFile", "ext", "dereferenced", "force", "removeUnusedComponents", "keepUrlReferences", "componentNamesStrategy", "componentRenamingConflicts", "skipDecorators", "addResource", "attach", "classifier"),
            parameters("bundle"),
        )
        assertEquals(configured + setOf("skipCheckConfig", "severity", "format"), parameters("check-config"))
        assertEquals(api + setOf("skipStats", "format", "outputFile"), parameters("stats"))
        assertEquals(api + setOf("skipScore", "format", "operationDetails", "outputFile", "minScore"), parameters("score"))
        assertEquals(api + setOf("skipJoin", "outputFile", "prefixTagsWithInfoProp", "prefixTagsWithFilename", "prefixComponentsWithInfoProp", "withoutXTagGroups"), parameters("join"))
        assertEquals(runtime + setOf("skipSplit", "api", "outputDirectory", "separator"), parameters("split"))

        for (goal in listOf("lint", "bundle", "check-config", "stats", "score", "join", "split", "help")) {
            val xml = mojoXml(goal)
            val description = Regex("<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1).orEmpty()
            assertTrue(description.isNotBlank(), "goal $goal has no description")
            for (parameter in parameterTag.findAll(xml)) {
                val body = parameter.groupValues[1]
                val name = Regex("<name>([^<]+)</name>").find(body)!!.groupValues[1]
                val parameterDescription = Regex("<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1).orEmpty()
                assertTrue(parameterDescription.isNotBlank(), "$goal parameter $name has no description")
            }
        }
    }
}
