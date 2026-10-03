package net.mwalser.openapi.toolkit.redocly

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
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

    @BeforeEach
    fun clearLogs() = logs.clear()

    private fun redocly(network: NetworkConfig = NetworkConfig()) = Redocly(runtime, log, network)

    @Test
    fun `reports the embedded redocly version`() {
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(runtime.redoclyVersion), runtime.redoclyVersion)
        assertEquals(RedoclyRuntime.EffectiveEngine.INTERPRETER, runtime.effectiveEngine)
    }

    @Test
    fun `lints an api using redocly yaml`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val result = redocly().lint(LintOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js))

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
        val result = redocly().lint(LintOptions(cwd = project.js, apis = listOf("openapi.json"), reportFormat = "checkstyle"))

        assertTrue(result.usedDefaultConfig)
        assertNull(result.configLint)
        assertNull(result.apis.single().alias)
        assertTrue(result.totals.errors + result.totals.warnings > 0)
        val report = assertNotNull(result.report)
        assertContains(report, "<checkstyle")
        assertContains(report, "<file name=\"openapi.json\">")
    }

    @ParameterizedTest
    @ValueSource(strings = ["lint", "bundle"])
    fun `ruleset overrides do not leak into later modules`(command: String, @TempDir dir: Path) {
        val firstModule = Files.createDirectories(dir.resolve("first"))
        val secondModule = Files.createDirectories(dir.resolve("second"))
        for (module in listOf(firstModule, secondModule)) {
            Files.writeString(module.resolve("openapi.yaml"), "openapi: 3.0.3\ninfo: { title: Test, version: '1' }\npaths: {}\n")
        }
        val overridden = LintOptions(cwd = firstModule.js, apis = listOf("openapi.yaml"), extends = listOf("minimal"))
        when (command) {
            "lint" -> {
                val result = redocly().lint(overridden)
                assertFalse(result.usedDefaultConfig)
                assertEquals(Totals(errors = 0, warnings = 1), result.totals)
            }
            "bundle" -> {
                val result = redocly().bundle(
                    BundleOptions(cwd = overridden.cwd, apis = overridden.apis, extends = overridden.extends, outputDirectory = "out"),
                )
                assertTrue(result.apis.single().written)
            }
        }

        // The same runtime must restore recommended rules when the next module supplies no override.
        val result = redocly().lint(LintOptions(cwd = secondModule.js, apis = listOf("openapi.yaml")))
        assertTrue(result.usedDefaultConfig)
        assertNull(result.configLint)
        assertEquals(Totals(errors = 1, warnings = 1), result.totals)
        assertEquals("error", result.apis.single().problems.single { it.ruleId == "no-empty-servers" }.severity)
        assertTrue(result.apis.single().problems.any { it.ruleId == "info-license" })
    }

    @Test
    fun `maxProblems bounds the console output but not the report`(@TempDir dir: Path) {
        val project = fixture("petstore3", dir)
        Files.delete(project.resolve("redocly.yaml"))
        val result = redocly().lint(LintOptions(cwd = project.js, apis = listOf("openapi.json"), maxProblems = 3, reportFormat = "json"))
        val api = result.apis.single()
        assertTrue(api.totals.errors + api.totals.warnings > 3)
        // stylish output: one line per problem after the file header
        assertEquals(3, api.output.lines().count { it.contains("  warning  ") || it.contains("  error  ") })
        assertContains(api.output, "increase with openapi.toolkit.maxProblems")
        val report = assertNotNull(result.report)
        assertContains(report, "\"errors\": ${api.totals.errors}")
        assertContains(report, "\"version\": \"${runtime.redoclyVersion}\"")
        assertEquals(api.totals.errors + api.totals.warnings, Regex("\"ruleId\"").findAll(report).count())
    }

    @Test
    fun `lints the configuration file and reports unknown rules`(@TempDir dir: Path) {
        val project = fixture("broken", dir)
        val result = redocly().lint(
            LintOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js, apis = listOf("openapi.yaml")),
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
            redocly().lint(LintOptions(cwd = project.js, apis = listOf("nope.yaml")))
        }
        assertEquals("CommandError", e.jsName)
        assertContains(e.message!!, "nope.yaml")
    }

    @Test
    fun `fails when no apis are configured`(@TempDir dir: Path) {
        val e = assertFailsWith<RedoclyException> { redocly().lint(LintOptions(cwd = dir.js)) }
        assertContains(e.message!!, "No APIs were provided")
    }

    @Test
    fun `generates an ignore file`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val first = redocly().lint(LintOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js, generateIgnoreFile = true))
        assertEquals(6, first.ignoreFile?.ignored)
        val ignoreFile = project.resolve(".redocly.lint-ignore.yaml")
        assertTrue(Files.exists(ignoreFile))
        assertContains(ignoreFile.readText(), "security-defined")

        val second = redocly().lint(LintOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js))
        assertEquals(Totals(errors = 0, warnings = 0, ignored = 6), second.totals)
    }

    @Test
    fun `implicit config and ignore files are looked up in cwd, not in the jvm working directory`(@TempDir dir: Path) {
        val module = fixture("petstore", dir.resolve("module"))
        val generated = redocly().lint(LintOptions(cwd = module.js, configPath = module.resolve("redocly.yaml").js, generateIgnoreFile = true))
        assertTrue(generated.ignoreFile!!.ignored > 0)
        Files.delete(module.resolve("redocly.yaml"))
        // a redocly.yaml above the module (as in a reactor root) must not be picked up either
        Files.writeString(dir.resolve("redocly.yaml"), "apis:\n  wrong:\n    root: missing.yaml\n")

        val result = redocly().lint(LintOptions(cwd = module.js, apis = listOf("openapi.yaml")))
        assertTrue(result.usedDefaultConfig)
        assertTrue(result.totals.ignored > 0, "module-local ignore file was not loaded")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `generates and reloads ignore files without a config`(overrideRuleset: Boolean, @TempDir dir: Path) {
        Files.writeString(dir.resolve("openapi.yaml"), "openapi: 3.0.3\ninfo: { title: Test, version: '1' }\npaths: {}\n")
        val options = LintOptions(
            cwd = dir.js,
            apis = listOf("openapi.yaml"),
            extends = if (overrideRuleset) listOf("minimal") else null,
        )
        val generated = redocly().lint(options.copy(generateIgnoreFile = true))
        val count = if (overrideRuleset) 1 else 2
        assertEquals(count, generated.ignoreFile?.ignored)
        assertTrue(Files.isRegularFile(dir.resolve(".redocly.lint-ignore.yaml")))
        assertFalse(Files.exists(dir.resolve("redocly.yaml")))

        val result = redocly().lint(options)
        assertEquals(!overrideRuleset, result.usedDefaultConfig)
        assertNull(result.configLint)
        assertEquals(Totals(ignored = count), result.totals)
    }

    @Test
    fun `bundles an api with external refs`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val result = redocly().bundle(
            BundleOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js, outputDirectory = "target/openapi"),
        )
        val api = result.apis.single()
        assertTrue(api.written)
        assertEquals(Totals(), api.totals)
        val out = hostPath(api.outputFile)
        assertEquals(project.resolve("target/openapi/petstore.yaml"), out)
        val yaml = out.readText()
        assertTrue(yaml.startsWith("openapi: 3.0.3\n"))
        assertContains(yaml, "components:\n  schemas:\n    pet:")
        assertContains(yaml, "\$ref: '#/components/schemas/pet'")
        assertFalse(yaml.contains("schemas/pet.yaml"))
    }

    @Test
    fun `resolves a two hundred deep ref chain`(@TempDir dir: Path) {
        // GraalJS recursion is bounded by the thread stack; this overflows on Maven's 1 MB main thread
        writeDeepRefChain(dir, depth = 200)
        val result = redocly().lint(LintOptions(cwd = dir.js, apis = listOf("openapi.yaml"), extends = listOf("minimal")))
        assertEquals(0, result.totals.errors, result.apis.single().problems.toString())
    }

    @Test
    fun `bundles to json and dereferenced`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val result = redocly().bundle(
            BundleOptions(
                cwd = project.js, configPath = project.resolve("redocly.yaml").js,
                outputDirectory = "ignored", outputFile = "out/spec.json", ext = "json", dereferenced = true,
            ),
        )
        val out = hostPath(result.apis.single().outputFile)
        assertEquals(project.resolve("out/spec.json"), out)
        val json = out.readText()
        assertTrue(json.startsWith("{\n  \"openapi\": \"3.0.3\""))
        assertFalse(json.contains("\$ref"))
    }

    @Test
    fun `does not write a bundle with errors unless forced`(@TempDir dir: Path) {
        val project = fixture("broken", dir)
        val opts = BundleOptions(cwd = project.js, apis = listOf("openapi.yaml"), outputDirectory = "out")
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
        val requests = mutableListOf<Pair<String, String>>()
        val serveSchema = { exchange: HttpExchange ->
            requests += (exchange.requestHeaders.getFirst("X-Token") ?: "<none>") to (exchange.requestHeaders.getFirst("X-Env") ?: "<none>")
            val body = "type: object\nproperties:\n  name:\n    type: string\n".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/yaml")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        withHttpServer(serveSchema) { base ->
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
                      - matches: '$base/**'
                        name: X-Env
                        envVariable: PATH
                """.trimIndent(),
            )
            val result = redocly().bundle(
                BundleOptions(cwd = dir.js, configPath = dir.resolve("redocly.yaml").js, apis = listOf("openapi.yaml"), outputDirectory = "out"),
            )
            assertEquals(Totals(), result.totals)
            assertContains(hostPath(result.apis.single().outputFile).readText(), "components:\n  schemas:\n    pet:")
            assertEquals(listOf("secret" to System.getenv("PATH")), requests)
        }
    }

    @Test
    fun `offline mode refuses remote refs without making a request`(@TempDir dir: Path) {
        var requests = 0
        withHttpServer({ requests++; it.sendResponseHeaders(500, -1); it.close() }) { base ->
            writeApiWithRef(dir, "$base/schema.yaml")
            val result = redocly(NetworkConfig(offline = true)).bundle(BundleOptions(cwd = dir.js, apis = listOf("openapi.yaml"), outputDirectory = "out"))
            val message = result.apis.single().problems.joinToString { it.message }
            assertContains(message, "Maven is offline (-o)")
            assertContains(message, "$base/schema.yaml")
            assertEquals(0, requests)
        }
    }

    @Test
    fun `a file that is not valid utf-8 is decoded leniently`(@TempDir dir: Path) {
        val latin1 = "openapi: 3.0.3\ninfo: { title: Caf\u00e9, version: '1' }\nservers: [{ url: https://api.example.test }]\npaths: {}\n"
        Files.write(dir.resolve("latin.yaml"), latin1.toByteArray(Charsets.ISO_8859_1))
        val result = redocly().lint(LintOptions(cwd = dir.js, apis = listOf("latin.yaml"), extends = listOf("minimal")))
        assertEquals(0, result.totals.errors, result.apis.single().problems.toString())
    }

    @Test
    fun `a refused connection is reported with code and url`(@TempDir dir: Path) {
        val unusedPort = ServerSocket(0).use { it.localPort }
        val url = "http://127.0.0.1:$unusedPort/schema.yaml"
        writeApiWithRef(dir, url)
        val result = redocly().bundle(BundleOptions(cwd = dir.js, apis = listOf("openapi.yaml"), outputDirectory = "out"))
        val message = result.apis.single().problems.joinToString { it.message }
        assertContains(message, "ECONNREFUSED")
        assertContains(message, url)
    }

    @Test
    fun `an http error status is reported with the url`(@TempDir dir: Path) {
        withHttpServer({ it.sendResponseHeaders(404, -1); it.close() }) { base ->
            writeApiWithRef(dir, "$base/missing.yaml")
            val result = redocly().bundle(BundleOptions(cwd = dir.js, apis = listOf("openapi.yaml"), outputDirectory = "out"))
            val message = result.apis.single().problems.joinToString { it.message }
            assertContains(message, "$base/missing.yaml")
            assertContains(message, "404")
        }
    }

    @Test
    fun `an unreadable file is reported with code and path`(@TempDir dir: Path) {
        val unreadable = dir.resolve("unreadable.yaml")
        Files.writeString(unreadable, "openapi: 3.0.3")
        val posix = runCatching { Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------")) }.isSuccess
        assumeTrue(posix && !Files.isReadable(unreadable), "file system does not enforce POSIX permissions")

        val error = assertFailsWith<RedoclyException> { redocly().lint(LintOptions(cwd = dir.js, apis = listOf("unreadable.yaml"))) }
        assertContains(error.message!!, "EACCES")
        assertContains(error.message!!, unreadable.js)
    }

    @Test
    fun `checks the configuration`(@TempDir dir: Path) {
        val project = fixture("broken", dir)
        val result = redocly().checkConfig(CheckConfigOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js))
        assertEquals(Totals(errors = 1), result.configLint?.totals)
        assertContains(result.configLint!!.output, "4:3  error    configuration struct  Property `no-unknown-rule` is not expected here.")

        Files.writeString(project.resolve("redocly.yaml"), "extends:\n  - recommended\nrules:\n  info-license: loud\n")
        val invalid = redocly().checkConfig(CheckConfigOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js, severity = "error"))
        assertEquals(1, invalid.configLint?.totals?.errors)
        assertContains(invalid.configLint!!.output, "redocly.yaml")
    }

    @Test
    fun `rejects custom plugins and names the affected configuration`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("openapi.yaml"), "openapi: 3.0.3\ninfo: { title: Test, version: '1' }\npaths: {}\n")
        Files.writeString(dir.resolve("redocly.yaml"), "plugins: [./acme.js]\nrules:\n  acme/required: error\napis:\n  test:\n    root: openapi.yaml\n")

        val error = assertFailsWith<RedoclyException> {
            redocly().lint(LintOptions(cwd = dir.js, configPath = dir.resolve("redocly.yaml").js))
        }
        assertContains(error.message!!, "Custom JavaScript plugins are not supported")
        assertContains(error.message!!, "./acme.js")
        assertContains(error.message!!, "acme/required")
    }

    @Test
    fun `configuration errors abort before any api is processed`(@TempDir dir: Path) {
        var requests = 0
        withHttpServer({ requests++; it.sendResponseHeaders(200, 0); it.close() }) { base ->
            Files.writeString(dir.resolve("redocly.yaml"), "rules:\n  info-license: loud\napis:\n  remote:\n    root: $base/openapi.yaml\n")
            val result = redocly().lint(LintOptions(cwd = dir.js, configPath = dir.resolve("redocly.yaml").js, lintConfig = "error"))
            assertTrue(result.configLint!!.totals.errors > 0)
            assertTrue(result.apis.isEmpty())
            assertEquals(0, requests)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["root", "api", "inline-api", "command-line"])
    fun `rejects plugins in inherited and api configurations`(scope: String, @TempDir dir: Path) {
        Files.writeString(dir.resolve("base.yaml"), "extends: [minimal]\nplugins: [./acme.js]\nrules:\n  acme/required: error\n")
        Files.writeString(dir.resolve("parent.yaml"), "extends: [./base.yaml]\n")
        val config = when (scope) {
            "root" -> "extends: [./parent.yaml]\n"
            "api" -> "apis:\n  test:\n    root: missing.yaml\n    extends: [./parent.yaml]\n"
            "inline-api" -> "apis:\n  test:\n    root: missing.yaml\n    plugins: [./acme.js]\n    rules:\n      acme/required: error\n"
            else -> null
        }
        config?.let { Files.writeString(dir.resolve("redocly.yaml"), it) }
        val error = assertFailsWith<RedoclyException> {
            redocly().lint(
                LintOptions(
                    cwd = dir.js, apis = listOf("missing.yaml"), lintConfig = "off",
                    extends = if (scope == "command-line") listOf("./parent.yaml") else null,
                ),
            )
        }
        assertContains(error.message!!, "Custom JavaScript plugins are not supported")
        assertContains(error.message!!, "./acme.js")
        assertContains(error.message!!, "acme/required")
    }

    @Test
    fun `bundle format follows the input when neither ext nor outputFile decide`(@TempDir dir: Path) {
        val project = fixture("petstore3", dir)
        val api = redocly().bundle(BundleOptions(cwd = project.js, apis = listOf("openapi.json"), outputDirectory = "out")).apis.single()
        assertEquals("json", api.ext)
        assertEquals(project.resolve("out/openapi.json"), hostPath(api.outputFile))
        assertTrue(hostPath(api.outputFile).readText().startsWith("{"))
    }

    @Test
    fun `rejects apis that would be bundled to the same file`(@TempDir dir: Path) {
        Files.createDirectories(dir.resolve("a"))
        Files.createDirectories(dir.resolve("b"))
        for (sub in listOf("a", "b")) Files.writeString(dir.resolve("$sub/openapi.yaml"), "openapi: 3.0.3\ninfo: { title: $sub, version: '1' }\npaths: {}\n")

        val error = assertFailsWith<RedoclyException> {
            redocly().bundle(BundleOptions(cwd = dir.js, apis = listOf("a/openapi.yaml", "b/openapi.yaml"), outputDirectory = "out"))
        }
        assertContains(error.message!!, "openapi.yaml")
        assertContains(error.message!!, "aliases")
    }

    @Test
    fun `warns about a configured output it does not honor`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        Files.writeString(project.resolve("redocly.yaml"), "extends: [minimal]\napis:\n  petstore:\n    root: openapi.yaml\n    output: dist/petstore.yaml\n")
        val api = redocly().bundle(BundleOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js, outputDirectory = "out")).apis.single()
        assertEquals(project.resolve("out/petstore.yaml"), hostPath(api.outputFile))
        assertFalse(Files.exists(project.resolve("dist")))
        assertTrue(logs.any { it.startsWith("warn: Ignoring output 'dist/petstore.yaml' of api 'petstore'") }, logs.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["../escaped", "..\\escaped", "/escaped", "C:\\escaped", "\\\\server\\share\\escaped", "../out-other/escaped"])
    fun `rejects bundle aliases that escape the output directory before writing`(alias: String, @TempDir dir: Path) {
        Files.writeString(dir.resolve("openapi.yaml"), "openapi: 3.0.3\ninfo: { title: Test, version: '1' }\npaths: {}\n")
        Files.writeString(dir.resolve("redocly.yaml"), "extends: [minimal]\napis:\n  safe:\n    root: openapi.yaml\n  '$alias':\n    root: openapi.yaml\n")
        Files.writeString(dir.resolve("escaped.yaml"), "existing content")

        val error = assertFailsWith<RedoclyException> {
            redocly().bundle(BundleOptions(cwd = dir.js, outputDirectory = "out"))
        }
        assertContains(error.message!!, "outside the bundle output directory")
        assertFalse(Files.exists(dir.resolve("out")))
        assertEquals("existing content", dir.resolve("escaped.yaml").readText())
    }

    @Test
    fun `allows nested bundle aliases and explicit output files`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("openapi.yaml"), "openapi: 3.0.3\ninfo: { title: Test, version: '1' }\npaths: {}\n")
        Files.writeString(dir.resolve("redocly.yaml"), "extends: [minimal]\napis:\n  nested/api:\n    root: openapi.yaml\n")
        val options = BundleOptions(cwd = dir.js, outputDirectory = "out")
        val nested = redocly().bundle(options).apis.single()
        assertEquals(dir.resolve("out/nested/api.yaml"), hostPath(nested.outputFile))
        assertTrue(nested.written)

        val explicit = redocly().bundle(options.copy(outputFile = "chosen.yaml")).apis.single()
        assertEquals(dir.resolve("chosen.yaml"), hostPath(explicit.outputFile))
        assertTrue(explicit.written)
    }

    @Test
    fun `an api requested by url is matched to its alias`(@TempDir dir: Path) {
        val spec = "openapi: 3.0.3\ninfo: { title: Remote, version: '1' }\nservers: [{ url: https://api.example.test }]\npaths: {}\n".toByteArray()
        val serveSpec = { exchange: HttpExchange ->
            exchange.sendResponseHeaders(200, spec.size.toLong())
            exchange.responseBody.use { it.write(spec) }
        }
        withHttpServer(serveSpec) { base ->
            val url = "$base/openapi.yaml"
            Files.writeString(dir.resolve("redocly.yaml"), "extends: [minimal]\napis:\n  remote:\n    root: $url\n")
            val api = redocly().lint(LintOptions(cwd = dir.js, configPath = dir.resolve("redocly.yaml").js, apis = listOf(url))).apis.single()
            assertEquals("remote", api.alias)
            assertEquals(url, api.path)
        }
    }

    @Test
    fun `routes redocly logging to the host`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        redocly().lint(LintOptions(cwd = project.js, configPath = project.resolve("redocly.yaml").js))
        // nothing is printed by the JS side during a normal lint: all output is returned as data
        assertEquals(emptyList(), logs.filter { !it.startsWith("debug:") })
    }

    /** Serves every request with [handler] on an ephemeral port and hands the base URL to [test]. */
    private fun withHttpServer(handler: (HttpExchange) -> Unit, test: (baseUrl: String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/", handler)
            start()
        }
        try {
            test("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }
}
