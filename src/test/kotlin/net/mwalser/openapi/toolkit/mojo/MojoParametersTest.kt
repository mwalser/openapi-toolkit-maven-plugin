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
        override val skipGoal get() = SkipParameter("openapi.toolkit.lint.skip", mojo.skipLint)
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
        assertRejected("openapi.toolkit.lint.format", LintMojo().apply { format = "typo" })
        assertRejected("openapi.toolkit.lint.format", LintMojo().apply { format = "junit" }) // machine formats are for reportFile
        assertRejected("openapi.toolkit.lint.reportFormat", LintMojo().apply { reportFile = File("x"); reportFormat = "typo" })
        assertRejected("openapi.toolkit.lintConfig", LintMojo().apply { lintConfig = "loud" })
        assertRejected("openapi.toolkit.maxProblems", LintMojo().apply { maxProblems = 0 })
        assertRejected("openapi.toolkit.bundle.ext", BundleMojo().apply { ext = "xml" })
        assertRejected("openapi.toolkit.bundle.ext", BundleMojo().apply { ext = "yaml"; outputFile = File("spec.json") })
        assertRejected("openapi.toolkit.bundle.componentNamesStrategy", BundleMojo().apply { componentNamesStrategy = "auto" })
        assertRejected("openapi.toolkit.bundle.componentRenamingConflicts", BundleMojo().apply { componentRenamingConflicts = "fatal" })
        assertRejected("openapi.toolkit.bundle.classifier", BundleMojo().apply { attach = true; classifier = " " })
        assertRejected("openapi.toolkit.checkConfig.severity", CheckConfigMojo().apply { severity = "off" })
        assertRejected("openapi.toolkit.checkConfig.format", CheckConfigMojo().apply { format = "typo" })
        assertRejected("openapi.toolkit.stats.format", StatsMojo().apply { format = "xml" })
        assertRejected("openapi.toolkit.score.format", ScoreMojo().apply { format = "markdown" })
        assertRejected("openapi.toolkit.score.minScore", ScoreMojo().apply { minScore = Double.NaN })
        assertRejected("openapi.toolkit.score.minScore", ScoreMojo().apply { minScore = 101.0 })
        assertRejected("openapi.toolkit.split.separator", SplitMojo().apply { separator = " " })
        assertRejected("openapi.toolkit.stats.outputFile", StatsMojo().apply { outputFile = File("stats.json"); apis = mutableListOf("one", "two") })
        assertRejected("openapi.toolkit.score.outputFile", ScoreMojo().apply { outputFile = File("score.json"); apis = mutableListOf("one", "two") })
        assertRejected("openapi.toolkit.buildDocs.outputFile", BuildDocsMojo().apply { outputFile = File("docs.html"); apis = mutableListOf("one", "two") })
        assertRejected("openapi.toolkit.join.prefixTagsWithFilename", join().apply { prefixTagsWithInfoProp = "title"; prefixTagsWithFilename = true })
        assertRejected("openapi.toolkit.join.withoutXTagGroups", join().apply { prefixTagsWithInfoProp = "title"; withoutXTagGroups = true })
        assertRejected("openapi.toolkit.join.withoutXTagGroups", join().apply { prefixTagsWithFilename = true; withoutXTagGroups = true })
    }

    @Test
    fun `a missing documentation template is rejected before anything runs`(@TempDir dir: Path) {
        val mojo = BuildDocsMojo().apply {
            project = MavenProject().also { it.file = dir.resolve("pom.xml").toFile() }
            template = File("missing.hbs")
        }
        val error = assertFailsWith<MojoExecutionException> { mojo.execute() }
        assertContains(error.message!!, "Template not found: ${dir.resolve("missing.hbs")} (set by openapi.toolkit.buildDocs.template)")
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

    /** Eclipse (m2e) reports every execution the mapping does not cover as an error in the POM. */
    @Test
    fun `every goal is covered by the m2e lifecycle mapping`() {
        val descriptorFile = Path.of("target/classes/META-INF/maven/plugin.xml")
        assumeTrue(Files.exists(descriptorFile), "plugin descriptor not generated (run via Maven)")
        val goals = Regex("<mojo>\\s*<goal>([^<]+)</goal>").findAll(descriptorFile.readText()).map { it.groupValues[1] }.toSet() - "help"
        val mapping = Path.of("src/main/resources/META-INF/m2e/lifecycle-mapping-metadata.xml").readText()
        val entries = Regex("<pluginExecution>(.*?)</pluginExecution>", RegexOption.DOT_MATCHES_ALL).findAll(mapping).map { it.groupValues[1] }.toList()
        fun goalsOf(entry: String) = Regex("<goal>([^<]+)</goal>").findAll(entry).map { it.groupValues[1] }.toSet()

        assertEquals(goals, entries.flatMap(::goalsOf).toSet(), "goals of the plugin descriptor versus the m2e mapping")
        val executed = entries.filter { it.contains("<execute>") }
        assertEquals(setOf("bundle", "join", "build-docs"), executed.flatMap(::goalsOf).toSet(), "goals whose output may be packaged run in Eclipse builds")
        assertTrue(executed.all { it.contains("<runOnIncremental>false</runOnIncremental>") }, "executed goals run on full builds only")
    }

    @Test
    fun `goals advertise exactly the parameters they honor`() {
        val descriptorFile = Path.of("target/classes/META-INF/maven/plugin.xml")
        assumeTrue(Files.exists(descriptorFile), "plugin descriptor not generated (run via Maven)")
        val descriptor = descriptorFile.readText()
        assertContains(descriptor, "<goalPrefix>openapi-toolkit</goalPrefix>")
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
        assertEquals(
            api + setOf("skipBuildDocs", "outputDirectory", "outputFile", "title", "disableGoogleFont", "template", "templateOptions", "redocOptions", "addResource", "resourceTargetPath"),
            parameters("build-docs"),
        )

        for (goal in listOf("lint", "bundle", "check-config", "stats", "score", "join", "split", "build-docs", "help")) {
            val xml = mojoXml(goal)
            val description = Regex("<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1).orEmpty()
            assertTrue(description.isNotBlank(), "goal $goal has no description")
            for (parameter in parameterTag.findAll(xml)) {
                val body = parameter.groupValues[1]
                val name = Regex("<name>([^<]+)</name>").find(body)!!.groupValues[1]
                val parameterDescription = Regex("<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1).orEmpty()
                assertTrue(parameterDescription.isNotBlank(), "$goal parameter $name has no description")
                // maps (templateOptions, redocOptions) cannot be set through a single property
                if (goal != "help" && name !in setOf("project", "session") && !body.contains("<type>java.util.Map</type>")) {
                    val expression = Regex("<$name\\b[^>]*>([^<]*)</$name>").find(xml)?.groupValues?.get(1).orEmpty()
                    assertTrue(expression.startsWith("\${openapi.toolkit."), "$goal parameter $name has an unexpected property: $expression")
                }
            }
        }
    }
}
