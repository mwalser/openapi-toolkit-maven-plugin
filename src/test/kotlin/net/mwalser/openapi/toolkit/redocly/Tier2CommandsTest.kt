package net.mwalser.openapi.toolkit.redocly

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    @Test
    fun `stats in all formats`(@TempDir dir: Path) {
        val project = fixture("petstore", dir)
        val configPath = project.resolve("redocly.yaml").js

        val stylish = redocly().stats(StatsOptions(cwd = project.js, configPath = configPath)).apis.single()
        assertEquals("petstore", stylish.alias)
        assertContains(stylish.output, "Path Items: 2")
        assertContains(stylish.output, "Operations: 2")
        assertContains(stylish.output, "Schemas: 1")
        assertFalse(stylish.output.contains("Document:"), stylish.output)
        assertFalse(stylish.output.contains("processed in"), stylish.output)

        val json = redocly().stats(StatsOptions(cwd = project.js, configPath = configPath, apis = listOf("openapi.yaml"), format = "json"))
        val stats = assertNotNull(json.apis.single().stats)
        @Suppress("UNCHECKED_CAST")
        assertEquals(2, (stats["operations"] as Map<String, Any?>)["total"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(1, (stats["schemas"] as Map<String, Any?>)["total"])

        val markdown = redocly().stats(StatsOptions(cwd = project.js, configPath = configPath, format = "markdown")).apis.single()
        assertContains(markdown.output, "| Feature  | Count  |")
        assertContains(markdown.output, "Operations")

        // several APIs are processed in one go
        Files.writeString(project.resolve("redocly.yaml"), "extends: [minimal]\napis:\n  one:\n    root: openapi.yaml\n  two:\n    root: openapi.yaml\n")
        val both = redocly().stats(StatsOptions(cwd = project.js, configPath = configPath))
        assertEquals(listOf("one", "two"), both.apis.map { it.alias })
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
            JoinOptions(cwd = project.js, apis = listOf("openapi.yaml", "orders.yaml"), output = "out/joined.yaml", prefixTagsWithInfoProp = "title"),
        )
        assertEquals(project.resolve("out/joined.yaml").js, result.outputFile)
        assertEquals(listOf(project.resolve("openapi.yaml").js, second.js), result.apis.map { it.path })
        val joined = hostPath(result.outputFile).readText()
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
            redocly().join(JoinOptions(cwd = project.js, apis = listOf("openapi.yaml"), output = "out/joined.yaml"))
        }
        assertContains(e.message!!, "At least 2 APIs")
    }

    @Test
    fun `a failing join still reports the bundling problems it printed`(@TempDir dir: Path) {
        writeApiWithRef(dir, "./missing.yaml")
        Files.writeString(dir.resolve("two.yaml"), "openapi: 3.0.3\ninfo: { title: Two, version: '1' }\npaths: {}\n")
        val error = assertFailsWith<RedoclyException> {
            redocly().join(JoinOptions(cwd = dir.js, apis = listOf("openapi.yaml", "two.yaml"), output = "joined.yaml"))
        }
        assertContains(error.message!!, "missing.yaml")
        assertTrue(error.message!!.lines().size > 1, error.message)
    }

    @Test
    fun `splits a description into files`(@TempDir dir: Path) {
        val project = fixture("petstore3", dir)
        val result = redocly().split(SplitOptions(cwd = project.js, api = "openapi.json", outDir = "split"))
        val outDir = hostPath(result.outDir)
        assertTrue(Files.isRegularFile(outDir.resolve("openapi.json")))
        assertTrue(Files.isDirectory(outDir.resolve("paths")))
        assertTrue(Files.isDirectory(outDir.resolve("components/schemas")))
        assertTrue(Files.isRegularFile(outDir.resolve("components/schemas/Pet.json")))
        assertContains(outDir.resolve("openapi.json").readText(), "paths/pet.json")
        assertContains(result.output, "is successfully split")
    }

    @Test
    fun `scores a description`(@TempDir dir: Path) {
        val project = fixture("petstore3", dir)
        val stylish = redocly().score(ScoreOptions(cwd = project.js, apis = listOf("openapi.json"))).apis.single()
        assertContains(stylish.output, "Agent Readiness:")
        assertTrue(stylish.agentReadiness in 0.0..100.0, "agentReadiness=${stylish.agentReadiness}")

        val json = redocly().score(ScoreOptions(cwd = project.js, apis = listOf("openapi.json"), format = "json")).apis.single()
        val score = assertNotNull(json.score)
        assertEquals(stylish.agentReadiness, json.agentReadiness)
        assertTrue(score.containsKey("hotspots"))
    }
}
