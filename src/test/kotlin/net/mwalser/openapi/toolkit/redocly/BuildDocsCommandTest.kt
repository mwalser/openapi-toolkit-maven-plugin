package net.mwalser.openapi.toolkit.redocly

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The build-docs command: Redoc 3's server-side rendering inside the plugin's context. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuildDocsCommandTest {

    private lateinit var runtime: RedoclyRuntime

    @BeforeAll
    fun start() {
        runtime = RedoclyRuntime.create(EngineMode.INTERPRETER)
    }

    @AfterAll
    fun stop() = runtime.close()

    private fun redocly() = Redocly(runtime)

    private fun options(project: Path, vararg apis: String, configure: BuildDocsOptions.() -> BuildDocsOptions = { this }) =
        BuildDocsOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js, apis = apis.toList(), outputDirectory = "docs").configure()

    private fun page(result: BuildDocsResult) = hostPath(result.apis.single().outputFile).readText()

    /**
     * The fixture's `expected-pages.sha256` holds the pages `redocly build-docs` renders with telemetry off
     * (`scripts/build-docs-expected.sh`). They are rendered one after another in the shared context, with a failed
     * render and one without the Redocly logo in between, since Redoc keeps state in modules: every page must come
     * out as from a fresh CLI process.
     */
    @Test
    fun `pages are identical to the Redocly CLI's, also after earlier and failed renders`(@TempDir dir: Path) {
        val project = fixture("build-docs", dir)
        val expected = project.resolve("expected-pages.sha256").readLines().filter { it.isNotBlank() }
            .associate { line -> line.substringAfter("  ") to line.substringBefore("  ") }

        val result = redocly().buildDocs(options(project))
        assertEquals(listOf("pets", "legacy", "events", "schema"), result.apis.map { it.alias })
        assertEquals(listOf("openapi", "openapi", "asyncapi", "graphql"), result.apis.map { it.specType })
        assertEquals(listOf("Pets", "Legacy Pets", "Pet Events", "ReDoc documentation"), result.apis.map { it.title })
        Files.writeString(project.resolve("invalid.yaml"), "openapi: 3.1.0\ninfo: {title: [unclosed\n")
        assertFailsWith<RedoclyException> { redocly().buildDocs(options(project, "invalid.yaml")) }
        redocly().buildDocs(options(project, "events") { copy(outputFile = "other/events.html", inlineBundle = true, removeRedoclyLogo = true) })
        redocly().buildDocs(options(project, "pets") { copy(outputFile = "docs/pets-inline.html", inlineBundle = true) })

        val pages = expected.keys.associateWith { project.resolve("docs/$it") }
        val actual = pages.mapValues { (_, page) -> sha256(page.readBytes()) }
        if (actual != expected) {
            val kept = Files.createDirectories(Path.of("target/build-docs-pages"))
            pages.values.forEach { Files.copy(it, kept.resolve(it.fileName), StandardCopyOption.REPLACE_EXISTING) }
            assertEquals(expected, actual, "pages differ from the CLI's; compare $kept with the output of scripts/build-docs-expected.sh")
        }
        assertFalse(pages.getValue("pets.html").readText().contains("reindexPets"), "the remove-x-internal decorator applies")
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `renders a page that hydrates from the cdn, and leaves the context fit for other goals`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val result = redocly().buildDocs(options(project))
        val api = result.apis.single()
        assertEquals("petstore", api.alias)
        assertEquals(project.resolve("openapi.yaml").js, api.path)
        assertEquals(project.resolve("docs/petstore.html").js, api.outputFile)
        assertEquals("Petstore", api.title)
        assertEquals("openapi", api.specType)
        assertTrue(api.durationMillis > 0)
        assertTrue(result.redocVersion.matches(Regex("""3\.\d+\.\d+""")), result.redocVersion)

        val page = page(result)
        assertContains(page, "<title>Petstore</title>")
        val script = "https://cdn.redocly.com/redoc/v${result.redocVersion}/bundles/redoc.standalone.js"
        assertContains(page, """<link rel="modulepreload" href="$script" integrity="sha384-""")
        assertContains(page, """import { hydrate } from "$script";""")
        assertContains(page, """<div id="redoc">""")
        assertContains(page, "List pets") // pre-rendered reference, not just the definition
        assertContains(page, """"operationId":"getPet"""") // the definition the page hydrates from

        // the globals the docs bundle installs leave the other commands untouched
        val lint = redocly().lint(LintOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js))
        assertEquals(1, lint.apis.size)
    }

    /** Unlike the CLI, the pages send Redoc telemetry only when asked to; the CLI's switches still turn it off. */
    @Test
    fun `sends telemetry only when it is turned on and not turned off in the configuration`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        fun telemetryDisabled(page: String) = Regex(""""disableTelemetry":(true|false)""").find(page)!!.groupValues[1].toBoolean()

        assertTrue(telemetryDisabled(page(redocly().buildDocs(options(project)))))
        assertFalse(telemetryDisabled(page(redocly().buildDocs(options(project) { copy(telemetry = true) }))))
        Files.writeString(project.resolve("redocly.yaml"), Files.readString(project.resolve("redocly.yaml")) + "telemetry: off\n")
        assertTrue(telemetryDisabled(page(redocly().buildDocs(options(project) { copy(telemetry = true) }))))
    }

    @Test
    fun `removes the Redocly logo link from the rendered reference and the inlined script`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val attribution = "href:`https://redocly.com/redoc/`"
        val withLogo = page(redocly().buildDocs(options(project) { copy(outputFile = "docs/with.html", inlineBundle = true) }))
        assertContains(withLogo, "API docs by")
        assertContains(withLogo, attribution)

        val without = page(redocly().buildDocs(options(project) { copy(outputFile = "docs/without.html", inlineBundle = true, removeRedoclyLogo = true) }))
        assertFalse(without.contains("API docs by"), "the server-rendered link is left out")
        assertFalse(without.contains(attribution), "the inlined script does not add it back")
        assertContains(without, "List pets")

        val cdn = assertFailsWith<RedoclyException> { redocly().buildDocs(options(project) { copy(removeRedoclyLogo = true) }) }
        assertContains(cdn.message!!, "removeRedoclyLogo requires inlineBundle")
    }

    @Test
    fun `takes the template from the configuration`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        Files.writeString(
            project.resolve("redocly.yaml"),
            "extends: [minimal]\napis:\n  petstore:\n    root: openapi.yaml\nopenapi:\n  htmlTemplate: templates/page.hbs\n",
        )
        Files.createDirectories(project.resolve("templates"))
        Files.writeString(project.resolve("templates/page.hbs"), "<!DOCTYPE html>\n<html><head><title>{{title}}</title>{{{redocHead}}}</head><body><!-- from redocly.yaml -->{{{redocHTML}}}</body></html>\n")
        assertContains(page(redocly().buildDocs(options(project))), "<!-- from redocly.yaml -->")
    }

    @Test
    fun `applies title, template, template options and redoc options from both sources`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        Files.writeString(
            project.resolve("redocly.yaml"),
            "extends: [minimal]\napis:\n  petstore:\n    root: openapi.yaml\nopenapi:\n  hideDownloadButtons: true\n  jsonSamplesExpandLevel: 3\n",
        )
        val template = Files.writeString(
            project.resolve("page.hbs"),
            "<!DOCTYPE html>\n<html><head><title>{{title}}</title>{{{redocHead}}}</head>\n" +
                "<body><h1>{{templateOptions.company}} reference</h1>{{{redocHTML}}}</body></html>\n",
        )
        val result = redocly().buildDocs(
            options(project, "petstore") {
                copy(
                    outputFile = "site/reference.html",
                    title = "Pets API",
                    template = template.js,
                    templateOptions = mapOf("company" to "ACME"),
                    redocOptions = mapOf("jsonSamplesExpandLevel" to "all", "downloadUrls" to """[{"title":"YAML","url":"openapi.yaml"}]"""),
                )
            },
        )
        val api = result.apis.single()
        assertEquals(project.resolve("site/reference.html").js, api.outputFile)
        assertEquals("Pets API", api.title)
        val page = page(result)
        assertContains(page, "<title>Pets API</title>")
        assertContains(page, "<h1>ACME reference</h1>")
        assertContains(page, """"hideDownloadButtons":true""") // from redocly.yaml
        assertContains(page, """"jsonSamplesExpandLevel":"all"""") // the Maven parameter wins over redocly.yaml
        assertContains(page, """"downloadUrls":[{"title":"YAML","url":"openapi.yaml"}]""") // nested option given as JSON
    }

    @Test
    fun `rejects invalid input before rendering anything`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        Files.writeString(project.resolve("redocly.yaml"), "extends: [minimal]\napis:\n  one:\n    root: openapi.yaml\n  two:\n    root: openapi.yaml\n")

        val twoApis = assertFailsWith<RedoclyException> { redocly().buildDocs(options(project) { copy(outputFile = "docs/api.html") }) }
        assertContains(twoApis.message!!, "openapi.toolkit.buildDocs.outputFile can only be used with a single API, but 2 were selected")

        val missing = assertFailsWith<RedoclyException> { redocly().buildDocs(options(project, "nope.yaml")) }
        assertContains(missing.message!!, "API description not found: nope.yaml (not an alias in the configuration file (known: one, two) and not an existing file)")

        val badJson = assertFailsWith<RedoclyException> { redocly().buildDocs(options(project, "one") { copy(redocOptions = mapOf("downloadUrls" to "[oops")) }) }
        assertContains(badJson.message!!, "Invalid JSON in the build-docs parameter redocOptions, option 'downloadUrls'")
        assertFalse(Files.exists(project.resolve("docs")), "nothing is written when the input is rejected")
    }

    @Test
    fun `names the description that could not be rendered`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("invalid.yaml"), "openapi: 3.1.0\ninfo: {title: [unclosed\n")
        val invalid = assertFailsWith<RedoclyException> { redocly().buildDocs(BuildDocsOptions(cwd = dir.js, apis = listOf("invalid.yaml"), outputDirectory = "docs")) }
        assertContains(invalid.message!!, "Could not build the documentation for invalid.yaml")

        Files.writeString(
            dir.resolve("workflow.yaml"),
            "arazzo: 1.0.1\ninfo: {title: Flows, version: '1'}\nsourceDescriptions: [{name: api, url: openapi.yaml, type: openapi}]\nworkflows: []\n",
        )
        val arazzo = assertFailsWith<RedoclyException> { redocly().buildDocs(BuildDocsOptions(cwd = dir.js, apis = listOf("workflow.yaml"), outputDirectory = "docs")) }
        assertContains(arazzo.message!!, "build-docs renders OpenAPI, AsyncAPI, and GraphQL. This file is Arazzo.")
        assertFalse(Files.exists(dir.resolve("docs")))
    }

    @Test
    fun `configuration errors stop the goal before rendering`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        Files.writeString(project.resolve("redocly.yaml"), "extends: [minimal]\napis:\n  petstore:\n    root: openapi.yaml\nrules:\n  no-such-rule: error\nunknownKey: 1\n")
        val result = redocly().buildDocs(options(project) { copy(lintConfig = "error") })
        val configLint = assertNotNull(result.configLint)
        assertTrue(configLint.totals.errors > 0, configLint.output)
        assertTrue(result.apis.isEmpty())
        assertFalse(Files.exists(project.resolve("docs")))
    }
}
