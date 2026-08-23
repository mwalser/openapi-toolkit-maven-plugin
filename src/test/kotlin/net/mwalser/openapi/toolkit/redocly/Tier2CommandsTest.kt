package net.mwalser.openapi.toolkit.redocly

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Tier2CommandsTest {

    private lateinit var runtime: RedoclyRuntime

    @BeforeAll
    fun start() {
        runtime = RedoclyRuntime.create(EngineMode.INTERPRETER)
    }

    @AfterAll
    fun stop() = runtime.close()

    private fun redocly() = Redocly(runtime)

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun fixture(name: String, target: Path): Path {
        Path.of("src/test/resources/fixtures", name).copyToRecursively(target, followLinks = false, overwrite = true)
        return target
    }

    @Test
    fun `stats in all formats`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val configPath = project.resolve("redocly.yaml").toString()

        val stylish = redocly().stats(StatsOptions(cwd = project.toString(), configPath = configPath))
        assertEquals("petstore", stylish.alias)
        assertContains(stylish.output, "Path Items: 2")
        assertContains(stylish.output, "Operations: 2")
        assertContains(stylish.output, "Schemas: 1")

        val json = redocly().stats(StatsOptions(cwd = project.toString(), configPath = configPath, api = "openapi.yaml", format = "json"))
        val stats = assertNotNull(json.stats)
        @Suppress("UNCHECKED_CAST")
        assertEquals(2, (stats["operations"] as Map<String, Any?>)["total"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(1, (stats["schemas"] as Map<String, Any?>)["total"])

        val markdown = redocly().stats(StatsOptions(cwd = project.toString(), configPath = configPath, format = "markdown"))
        assertContains(markdown.output, "| Feature  | Count  |")
        assertContains(markdown.output, "Operations")
    }

    @Test
    fun `joins two descriptions`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val second = project.resolve("orders.yaml")
        Files.writeString(
            second,
            """
            openapi: 3.0.3
            info: { title: Orders, version: 1.0.0 }
            tags:
              - name: orders
            paths:
              /orders:
                get:
                  operationId: listOrders
                  tags: [orders]
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/Order'
            components:
              schemas:
                Order:
                  type: object
                  properties:
                    id: { type: string }
            """.trimIndent(),
        )
        val result = redocly().join(
            JoinOptions(cwd = project.toString(), apis = listOf("openapi.yaml", "orders.yaml"), output = "out/joined.yaml", prefixTagsWithInfoProp = "title"),
        )
        assertEquals(project.resolve("out/joined.yaml").toString(), result.outputFile)
        val joined = Path.of(result.outputFile).readText()
        assertContains(joined, "/pets:")
        assertContains(joined, "/orders:")
        assertContains(joined, "Order:")
        assertContains(joined, "pet:")
        assertContains(joined, "x-tagGroups:")
        assertContains(joined, "Petstore_pets")
    }

    @Test
    fun `join requires at least two apis`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val e = assertFailsWith<RedoclyException> {
            redocly().join(JoinOptions(cwd = project.toString(), apis = listOf("openapi.yaml"), output = "out/joined.yaml"))
        }
        assertContains(e.message!!, "At least 2 APIs")
    }

    @Test
    fun `splits a description into files`(@TempDir dir: Path) {
        val project = fixture("petstore3", dir)
        val result = redocly().split(SplitOptions(cwd = project.toString(), api = "openapi.json", outDir = "split"))
        val outDir = Path.of(result.outDir)
        assertTrue(Files.isRegularFile(outDir.resolve("openapi.json")))
        assertTrue(Files.isDirectory(outDir.resolve("paths")))
        assertTrue(Files.isDirectory(outDir.resolve("components/schemas")))
        assertTrue(Files.isRegularFile(outDir.resolve("components/schemas/Pet.json")))
        assertContains(outDir.resolve("openapi.json").readText(), "paths/pet.json")
        assertContains(result.output, "is successfully split")
    }
}

class ScoreCommandTest {
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @Test
    fun `scores a description`(@TempDir dir: Path) {
        Path.of("src/test/resources/fixtures/petstore3").copyToRecursively(dir, followLinks = false, overwrite = true)
        RedoclyRuntime.create(EngineMode.INTERPRETER).use { runtime ->
            val redocly = Redocly(runtime)
            val stylish = redocly.score(ScoreOptions(cwd = dir.toString(), api = "openapi.json"))
            assertContains(stylish.output, "Agent Readiness:")
            val json = redocly.score(ScoreOptions(cwd = dir.toString(), api = "openapi.json", format = "json"))
            val score = assertNotNull(json.score)
            val readiness = (score["agentReadiness"] as Number).toDouble()
            assertTrue(readiness in 0.0..100.0, "agentReadiness=$readiness")
            assertTrue(score.containsKey("hotspots"))
        }
    }
}
