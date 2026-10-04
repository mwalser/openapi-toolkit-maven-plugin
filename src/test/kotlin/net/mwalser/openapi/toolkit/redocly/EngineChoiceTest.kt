package net.mwalser.openapi.toolkit.redocly

import net.mwalser.openapi.toolkit.redocly.RedoclyRuntime.EffectiveEngine
import org.graalvm.polyglot.Engine
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class EngineChoiceTest {

    private fun interpreter(): Engine = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build()

    @Test
    fun `auto mode falls back to the interpreter when the isolate cannot start`() {
        val built = mutableListOf<EffectiveEngine>()
        val choice = RedoclyRuntime.chooseEngine(EngineMode.AUTO, EffectiveEngine.ISOLATE) { engine ->
            built += engine
            if (engine == EffectiveEngine.ISOLATE) throw InternalError("no isolate support on this JDK")
            interpreter()
        }
        choice.engine.use {
            assertEquals(EffectiveEngine.INTERPRETER, choice.effective)
            assertEquals(listOf(EffectiveEngine.ISOLATE, EffectiveEngine.INTERPRETER), built)
            assertIs<InternalError>(choice.isolateFailure)
        }
    }

    @Test
    fun `auto mode keeps the isolate when it starts`() {
        val choice = RedoclyRuntime.chooseEngine(EngineMode.AUTO, EffectiveEngine.ISOLATE) { interpreter() }
        choice.engine.use {
            assertEquals(EffectiveEngine.ISOLATE, choice.effective)
            assertNull(choice.isolateFailure)
        }
    }

    @Test
    fun `a requested isolate does not fall back`() {
        assertFailsWith<InternalError> {
            RedoclyRuntime.chooseEngine(EngineMode.ISOLATE, EffectiveEngine.ISOLATE) { throw InternalError("no isolate support on this JDK") }
        }
    }

    @Test
    fun `the fallback warning names the jdk, the root cause and the way out`() {
        val failure = RedoclyException("engine failed", cause = InternalError(NoSuchMethodError("JavaLangAccess.addEnableNativeAccess")))
        val warning = RedoclyRuntime.isolateFallbackWarning(failure)
        assertContains(warning, "Java ${Runtime.version()}")
        assertContains(warning, "java.lang.NoSuchMethodError: JavaLangAccess.addEnableNativeAccess")
        assertContains(warning, "js-isolate-${RedoclyRuntime.isolatePlatform()}-community")
        assertContains(warning, "or use a JDK the isolate supports")
    }
}
