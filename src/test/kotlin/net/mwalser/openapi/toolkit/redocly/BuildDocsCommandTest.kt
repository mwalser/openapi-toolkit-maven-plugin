package net.mwalser.openapi.toolkit.redocly

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The build-docs command: Redoc's server-side rendering inside the plugin's context. */
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

    @Test
    fun `renders a page per api that hydrates from the cdn, and leaves the context fit for other goals`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val result = redocly().buildDocs(options(project))
        val api = result.apis.single()
        assertEquals("petstore", api.alias)
        assertEquals(project.resolve("openapi.yaml").js, api.path)
        assertEquals(project.resolve("docs/petstore.html").js, api.outputFile)
        assertEquals("Petstore", api.title)
        assertTrue(api.durationMillis > 0)
        assertTrue(result.redocVersion.matches(Regex("""\d+\.\d+\.\d+""")), result.redocVersion)

        val page = hostPath(api.outputFile).readText()
        assertContains(page, "<title>Petstore</title>")
        assertContains(page, """<script src="https://cdn.redocly.com/redoc/v${result.redocVersion}/bundles/redoc.standalone.js" integrity="sha384-""")
        assertContains(page, "fonts.googleapis.com")
        assertContains(page, """<div id="redoc">""")
        assertContains(page, "Redoc.hydrate(__redoc_state, container);")
        assertContains(page, "List pets") // pre-rendered reference, not just the state
        assertContains(page, "/pets/{petId}")
        assertContains(page, """"operationId":"getPet"""") // the state the page hydrates from

        // the globals the docs bundle installs leave the other commands untouched
        val lint = redocly().lint(LintOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js))
        assertEquals(1, lint.apis.size)
    }

    /** Redoc keeps page state in module singletons; the shared runtime renders many pages, also after failures. */
    @Test
    fun `pages inherit nothing from earlier renders, failed ones included`(@TempDir dir: Path) {
        val petstore = fixture("petstore", dir.resolve("petstore"))
        val petstore3 = fixture("petstore3", dir.resolve("petstore3"))
        fun render(project: Path, api: String) =
            hostPath(redocly().buildDocs(BuildDocsOptions(cwd = project.js, apis = listOf(api), outputDirectory = "docs")).apis.single().outputFile).readBytes()

        val first = render(petstore, "openapi.yaml")
        val state = searchIndex(String(render(petstore3, "openapi.json")))
        val store = state["store"] as List<*>
        assertTrue(store.any { it.toString().contains("addPet") }, store.toString())
        assertFalse(store.any { it.toString().contains("listPets") }, "search entries of the previous page: $store")
        @Suppress("UNCHECKED_CAST")
        val indexed = ((state["index"] as Map<String, Any?>)["fieldVectors"] as List<List<Any?>>).map { it[0].toString().substringAfter('/') }.toSet()
        assertEquals(store.size, indexed.size, "every search entry is indexed once")
        assertContentEquals(first, render(petstore, "openapi.yaml"))

        val broken = fixture("broken", dir.resolve("broken"))
        val failure = assertFailsWith<RedoclyException> { render(broken, "openapi.yaml") }
        assertContains(failure.message!!, "Could not build the documentation for openapi.yaml")
        assertContentEquals(first, render(petstore, "openapi.yaml"))
    }

    @Test
    fun `takes the template from the configuration and renders without a search index`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        Files.writeString(
            project.resolve("redocly.yaml"),
            "extends: [minimal]\napis:\n  petstore:\n    root: openapi.yaml\nopenapi:\n  disableSearch: true\n  htmlTemplate: templates/page.hbs\n",
        )
        Files.createDirectories(project.resolve("templates"))
        Files.writeString(project.resolve("templates/page.hbs"), "<!DOCTYPE html>\n<html><head><title>{{title}}</title>{{{redocHead}}}</head><body><!-- from redocly.yaml -->{{{redocHTML}}}</body></html>\n")
        val page = hostPath(redocly().buildDocs(options(project)).apis.single().outputFile).readText()
        assertContains(page, "<!-- from redocly.yaml -->")
        assertFalse(page.contains("searchIndex"), "disableSearch leaves the index out of the state")
    }

    /** The `__redoc_state` the page hydrates from, reduced to its search index. */
    @Suppress("UNCHECKED_CAST")
    private fun searchIndex(page: String): Map<String, Any?> {
        val json = Regex("const __redoc_state = (.*?);\n", RegexOption.DOT_MATCHES_ALL).find(page)!!.groupValues[1]
        val state = Json.mapper.readValue(json, Map::class.java) as Map<String, Any?>
        return state["searchIndex"] as Map<String, Any?>
    }

    @Test
    fun `applies title, template, template options and redoc options from both sources`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        Files.writeString(
            project.resolve("redocly.yaml"),
            "extends: [minimal]\napis:\n  petstore:\n    root: openapi.yaml\nopenapi:\n  hideDownloadButton: true\n  expandResponses: all\n",
        )
        val template = Files.writeString(
            project.resolve("page.hbs"),
            "<!DOCTYPE html>\n<html><head><title>{{title}}</title>{{{redocHead}}}</head>\n" +
                "<body><h1>{{templateOptions.company}} reference{{#if disableGoogleFont}} (system fonts){{/if}}</h1>{{{redocHTML}}}</body></html>\n",
        )
        val result = redocly().buildDocs(
            options(project, "petstore") {
                copy(
                    outputFile = "site/reference.html",
                    title = "Pets API",
                    disableGoogleFont = true,
                    template = template.js,
                    templateOptions = mapOf("company" to "ACME"),
                    redocOptions = mapOf("expandResponses" to "200", "theme" to """{"colors":{"primary":{"main":"#ff0000"}}}"""),
                )
            },
        )
        val api = result.apis.single()
        assertEquals(project.resolve("site/reference.html").js, api.outputFile)
        assertEquals("Pets API", api.title)
        val page = hostPath(api.outputFile).readText()
        assertContains(page, "<title>Pets API</title>")
        assertContains(page, "<h1>ACME reference (system fonts)</h1>")
        assertFalse(page.contains("fonts.googleapis.com"), "Google Fonts link should be left out")
        assertContains(page, """"hideDownloadButton":true""") // from redocly.yaml
        assertContains(page, """"expandResponses":"200"""") // the Maven parameter wins over redocly.yaml
        assertContains(page, "#ff0000") // nested option given as JSON
    }

    @Test
    fun `converts swagger 2 before rendering`(@TempDir dir: Path) {
        Files.writeString(
            dir.resolve("swagger.json"),
            """
            {
              "swagger": "2.0",
              "info": { "title": "Legacy Pets", "version": "1.0" },
              "host": "example.com",
              "basePath": "/v1",
              "schemes": ["https"],
              "paths": {
                "/pets": {
                  "get": {
                    "summary": "List pets",
                    "produces": ["application/json"],
                    "responses": { "200": { "description": "ok", "schema": { "type": "array", "items": { "${'$'}ref": "#/definitions/Pet" } } } }
                  }
                }
              },
              "definitions": { "Pet": { "type": "object", "properties": { "name": { "type": "string" } } } }
            }
            """.trimIndent(),
        )
        val result = redocly().buildDocs(BuildDocsOptions(cwd = dir.js, apis = listOf("swagger.json"), outputDirectory = "docs"))
        val page = hostPath(result.apis.single().outputFile).readText()
        assertContains(page, "<title>Legacy Pets</title>")
        assertContains(page, """"openapi":"3.0.0"""")
    }

    @Test
    fun `rejects invalid input before rendering anything`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        Files.writeString(project.resolve("redocly.yaml"), "extends: [minimal]\napis:\n  one:\n    root: openapi.yaml\n  two:\n    root: openapi.yaml\n")

        val twoApis = assertFailsWith<RedoclyException> { redocly().buildDocs(options(project) { copy(outputFile = "docs/api.html") }) }
        assertContains(twoApis.message!!, "openapi.toolkit.buildDocs.outputFile can only be used with a single API, but 2 were selected")

        val missing = assertFailsWith<RedoclyException> { redocly().buildDocs(options(project, "nope.yaml")) }
        assertContains(missing.message!!, "API description not found: nope.yaml (not an alias in the configuration file (known: one, two) and not an existing file)")

        val badJson = assertFailsWith<RedoclyException> { redocly().buildDocs(options(project, "one") { copy(redocOptions = mapOf("theme" to "{oops")) }) }
        assertContains(badJson.message!!, "Invalid JSON in the build-docs parameter redocOptions, option 'theme'")

        val broken = fixture("broken", dir.resolve("broken"))
        val unreadable = assertFailsWith<RedoclyException> {
            redocly().buildDocs(BuildDocsOptions(cwd = broken.js, apis = listOf("missing-file.yaml"), outputDirectory = "docs"))
        }
        assertContains(unreadable.message!!, "API description not found")
        assertFalse(Files.exists(project.resolve("docs")), "nothing is written when the input is rejected")
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
