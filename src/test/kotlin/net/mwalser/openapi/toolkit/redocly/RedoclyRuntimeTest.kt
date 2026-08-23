package net.mwalser.openapi.toolkit.redocly

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedoclyRuntimeTest {

    private lateinit var runtime: RedoclyRuntime
    private val logs = mutableListOf<String>()
    private val log = object : JsLog {
        override fun output(message: String) { logs += "output: $message" }
        override fun info(message: String) { logs += "info: $message" }
        override fun warn(message: String) { logs += "warn: $message" }
        override fun error(message: String) { logs += "error: $message" }
        override fun debug(message: String) { logs += "debug: $message" }
    }

    @BeforeAll
    fun start() {
        runtime = RedoclyRuntime.create(EngineMode.INTERPRETER)
    }

    @AfterAll
    fun stop() = runtime.close()

    private fun redocly() = Redocly(runtime, log)

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun fixture(name: String, target: Path): Path {
        val source = Path.of("src/test/resources/fixtures", name)
        source.copyToRecursively(target, followLinks = false, overwrite = true)
        return target
    }

    @Test
    fun `reports the embedded redocly version`() {
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(runtime.redoclyVersion), runtime.redoclyVersion)
        assertEquals(RedoclyRuntime.EffectiveEngine.INTERPRETER, runtime.effectiveEngine)
    }

    @Test
    fun `lints an api using redocly yaml`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val result = redocly().lint(LintOptions(cwd = project.toString(), configPath = project.resolve("redocly.yaml").toString()))

        assertFalse(result.usedDefaultConfig)
        assertEquals(1, result.apis.size)
        val api = result.apis.single()
        assertEquals("petstore", api.alias)
        assertEquals(Totals(errors = 2, warnings = 4), api.totals)
        assertEquals(result.totals, api.totals)
        // info-license is switched off and operation-description downgraded to warn in redocly.yaml
        assertTrue(api.problems.none { it.ruleId == "info-license" })
        assertEquals("warn", api.problems.first { it.ruleId == "operation-description" }.severity)
        val security = api.problems.first { it.ruleId == "security-defined" }
        assertEquals(13, security.location.single().line)
        assertEquals(5, security.location.single().col)
        assertTrue(security.location.single().file!!.endsWith("openapi.yaml"))
        // stylish output with paths relative to cwd
        assertContains(api.output, "openapi.yaml:")
        assertContains(api.output, "13:5  error    security-defined")
        assertNull(result.report)
        assertTrue(result.unused.isEmpty)
    }

    @Test
    fun `lints with the built-in recommended ruleset when there is no config`(@TempDir dir: Path) {
        val project = fixture("petstore3", dir)
        Files.delete(project.resolve("redocly.yaml"))
        val result = redocly().lint(LintOptions(cwd = project.toString(), apis = listOf("openapi.json"), reportFormat = "checkstyle"))

        assertTrue(result.usedDefaultConfig)
        assertNull(result.configLint)
        assertNull(result.apis.single().alias)
        assertTrue(result.totals.errors + result.totals.warnings > 0)
        assertContains(result.report!!, "<checkstyle")
        assertContains(result.report!!, "<file name=\"openapi.json\">")
    }

    @Test
    fun `maxProblems bounds the console output but not the report`(@TempDir dir: Path) {
        val project = fixture("petstore3", dir)
        Files.delete(project.resolve("redocly.yaml"))
        val result = redocly().lint(LintOptions(cwd = project.toString(), apis = listOf("openapi.json"), maxProblems = 3, reportFormat = "json"))
        val api = result.apis.single()
        assertTrue(api.totals.errors + api.totals.warnings > 3)
        // stylish output: one line per problem after the file header
        assertEquals(3, api.output.lines().count { it.contains("  warning  ") || it.contains("  error  ") })
        assertContains(result.report!!, "\"errors\": ${api.totals.errors}")
        assertEquals(api.totals.errors + api.totals.warnings, Regex("\"ruleId\"").findAll(result.report!!).count())
    }

    @Test
    fun `lints the configuration file and reports unknown rules`(@TempDir dir: Path) {
        val project = fixture("broken", dir)
        val result = redocly().lint(
            LintOptions(cwd = project.toString(), configPath = project.resolve("redocly.yaml").toString(), apis = listOf("openapi.yaml")),
        )
        assertNotNull(result.configLint)
        assertEquals(listOf("no-unknown-rule"), result.unused.rules)
        // the missing $ref target is an error of the api
        assertTrue(result.totals.errors > 0)
        assertTrue(result.apis.single().problems.any { it.message.contains("missing.yaml") }, result.apis.single().problems.toString())
    }

    @Test
    fun `fails for a missing api`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val e = assertFailsWith<RedoclyException> {
            redocly().lint(LintOptions(cwd = project.toString(), apis = listOf("nope.yaml")))
        }
        assertEquals("CommandError", e.jsName)
        assertContains(e.message!!, "nope.yaml")
    }

    @Test
    fun `fails when no apis are configured`(@TempDir dir: Path) {
        val e = assertFailsWith<RedoclyException> { redocly().lint(LintOptions(cwd = dir.toString())) }
        assertContains(e.message!!, "No APIs were provided")
    }

    @Test
    fun `generates an ignore file`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val first = redocly().lint(LintOptions(cwd = project.toString(), configPath = project.resolve("redocly.yaml").toString(), generateIgnoreFile = true))
        assertEquals(6, first.ignoreFile?.ignored)
        val ignoreFile = project.resolve(".redocly.lint-ignore.yaml")
        assertTrue(Files.exists(ignoreFile))
        assertContains(ignoreFile.readText(), "security-defined")

        val second = redocly().lint(LintOptions(cwd = project.toString(), configPath = project.resolve("redocly.yaml").toString()))
        assertEquals(Totals(errors = 0, warnings = 0, ignored = 6), second.totals)
    }

    @Test
    fun `bundles an api with external refs`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val result = redocly().bundle(
            BundleOptions(cwd = project.toString(), configPath = project.resolve("redocly.yaml").toString(), outputDirectory = "target/openapi"),
        )
        val api = result.apis.single()
        assertTrue(api.written)
        assertEquals(Totals(), api.totals)
        val out = Path.of(api.outputFile)
        assertEquals(project.resolve("target/openapi/petstore.yaml"), out)
        val yaml = out.readText()
        assertTrue(yaml.startsWith("openapi: 3.0.3\n"))
        assertContains(yaml, "components:\n  schemas:\n    pet:")
        assertContains(yaml, "\$ref: '#/components/schemas/pet'")
        assertFalse(yaml.contains("schemas/pet.yaml"))
    }

    @Test
    fun `bundles to json and dereferenced`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val result = redocly().bundle(
            BundleOptions(
                cwd = project.toString(), configPath = project.resolve("redocly.yaml").toString(),
                outputDirectory = "ignored", outputFile = "out/spec.json", ext = "json", dereferenced = true,
            ),
        )
        val out = Path.of(result.apis.single().outputFile)
        assertEquals(project.resolve("out/spec.json"), out)
        val json = out.readText()
        assertTrue(json.startsWith("{\n  \"openapi\": \"3.0.3\""))
        assertFalse(json.contains("\$ref"))
    }

    @Test
    fun `does not write a bundle with errors unless forced`(@TempDir dir: Path) {
        val project = fixture("broken", dir)
        val opts = BundleOptions(cwd = project.toString(), apis = listOf("openapi.yaml"), outputDirectory = "out")
        val result = redocly().bundle(opts)
        assertFalse(result.apis.single().written)
        assertTrue(result.totals.errors > 0)
        assertFalse(Files.exists(project.resolve("out/openapi.yaml")))

        val forced = redocly().bundle(opts.copy(force = true))
        assertTrue(forced.apis.single().written)
        assertTrue(Files.exists(project.resolve("out/openapi.yaml")))
    }

    @Test
    fun `resolves http refs through the host`(@TempDir dir: Path) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = mutableListOf<String>()
        server.createContext("/schemas/pet.yaml") { exchange ->
            requests += exchange.requestHeaders.getFirst("X-Token") ?: "<none>"
            val body = "type: object\nproperties:\n  name:\n    type: string\n".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/yaml")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            Files.writeString(
                dir.resolve("openapi.yaml"),
                """
                openapi: 3.0.3
                info: { title: Remote, version: '1' }
                paths:
                  /pets:
                    get:
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema:
                                ${'$'}ref: '$base/schemas/pet.yaml'
                """.trimIndent(),
            )
            Files.writeString(
                dir.resolve("redocly.yaml"),
                """
                extends: [minimal]
                resolve:
                  http:
                    headers:
                      - matches: '$base/**'
                        name: X-Token
                        value: secret
                """.trimIndent(),
            )
            val result = redocly().bundle(
                BundleOptions(cwd = dir.toString(), configPath = dir.resolve("redocly.yaml").toString(), apis = listOf("openapi.yaml"), outputDirectory = "out"),
            )
            assertEquals(Totals(), result.totals)
            assertContains(Path.of(result.apis.single().outputFile).readText(), "components:\n  schemas:\n    pet:")
            assertEquals(listOf("secret"), requests)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `checks the configuration`(@TempDir dir: Path) {
        val project = fixture("broken", dir)
        val result = redocly().checkConfig(CheckConfigOptions(cwd = project.toString(), configPath = project.resolve("redocly.yaml").toString()))
        assertEquals(Totals(warnings = 1), result.configLint?.totals)
        assertContains(result.configLint!!.output, "4:3  warning  configuration struct  Property `no-unknown-rule` is not expected here.")

        Files.writeString(project.resolve("redocly.yaml"), "extends:\n  - recommended\nrules:\n  info-license: loud\n")
        val invalid = redocly().checkConfig(CheckConfigOptions(cwd = project.toString(), configPath = project.resolve("redocly.yaml").toString(), severity = "error"))
        assertEquals(1, invalid.configLint?.totals?.errors)
        assertContains(invalid.configLint!!.output, "redocly.yaml")
    }

    @Test
    fun `routes redocly logging to the host`(@TempDir dir: Path) {
        logs.clear()
        val project = fixture("petstore", dir)
        redocly().lint(LintOptions(cwd = project.toString(), configPath = project.resolve("redocly.yaml").toString()))
        // nothing is printed by the JS side during a normal lint: all output is returned as data
        assertEquals(emptyList(), logs.filter { !it.startsWith("debug:") })
    }
}
