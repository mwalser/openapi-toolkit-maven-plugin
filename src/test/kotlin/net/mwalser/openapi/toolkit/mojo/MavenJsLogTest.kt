package net.mwalser.openapi.toolkit.mojo

import org.apache.maven.plugin.logging.Log
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals

class MavenJsLogTest {
    private val entries = mutableListOf<String>()

    /** A Maven log that records `level: line`; every level is enabled. */
    private val log = Proxy.newProxyInstance(Log::class.java.classLoader, arrayOf(Log::class.java)) { _, method, args ->
        if (method.name.startsWith("is")) true else { entries += "${method.name}: ${args[0]}"; null }
    } as Log

    @Test
    fun `problem lines carry their own severity, the rest the level of the block`() {
        val stylish = """
            openapi.yaml:
              13:5  error    security-defined        Every operation should have security defined.
              22:7  warning  operation-4xx-response  Operation must have at least one 4XX response.

            < 1 more problem hidden > increase with openapi.toolkit.maxProblems
        """.trimIndent()

        MavenJsLog.problems(log, stylish, MavenJsLog.Level.ERROR)

        assertEquals(
            listOf(
                "error: openapi.yaml:",
                "error:   13:5  error    security-defined        Every operation should have security defined.",
                "warn:   22:7  warning  operation-4xx-response  Operation must have at least one 4XX response.",
                "error: ",
                "error: < 1 more problem hidden > increase with openapi.toolkit.maxProblems",
            ),
            entries,
        )
    }

    @Test
    fun `other formats are logged at the level of the block`() {
        MavenJsLog.problems(log, "| 13:5 | error | security-defined |\n", MavenJsLog.Level.WARN)
        assertEquals(listOf("warn: | 13:5 | error | security-defined |"), entries)
    }
}
