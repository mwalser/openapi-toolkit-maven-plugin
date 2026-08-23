package net.mwalser.openapi.toolkit.redocly

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JsPathsTest {
    private val original = JsPaths.windows

    @AfterEach
    fun restore() {
        JsPaths.windows = original
    }

    @Test
    fun `passes paths through on posix`() {
        JsPaths.windows = false
        assertEquals("/home/me/project", JsPaths.toJs("/home/me/project"))
        assertEquals("api/openapi.yaml", JsPaths.toJs("api/openapi.yaml"))
        assertEquals("/home/me/out/spec.yaml", JsPaths.toHost("/home/me/out/spec.yaml"))
    }

    @Test
    fun `maps windows drive paths to posix style and back`() {
        JsPaths.windows = true
        assertEquals("/C:/Users/me/project", JsPaths.toJs("C:\\Users\\me\\project"))
        assertEquals("/c:/Users/me/project", JsPaths.toJs("c:/Users/me/project"))
        assertEquals("api/openapi.yaml", JsPaths.toJs("api\\openapi.yaml"))
        assertEquals("petstore", JsPaths.toJs("petstore"))
        assertEquals("https://example.com/openapi.yaml", JsPaths.toJs("https://example.com/openapi.yaml"))

        assertEquals("C:\\Users\\me\\out\\spec.yaml", JsPaths.toHost("/C:/Users/me/out/spec.yaml"))
        assertEquals("out\\spec.yaml", JsPaths.toHost("out/spec.yaml"))
    }
}
