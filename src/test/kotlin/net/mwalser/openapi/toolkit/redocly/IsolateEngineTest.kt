package net.mwalser.openapi.toolkit.redocly

import org.graalvm.polyglot.Engine
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Only runs when a polyglot isolate is on the test classpath (`mvn test -Pisolate-tests`). */
class IsolateEngineTest {

    @Test
    fun `lints in a polyglot isolate`(@TempDir dir: Path) {
        assumeTrue(RedoclyRuntime.isIsolatePresent(), "no js-isolate-*-community artifact on the classpath")
        val project = fixture("petstore", dir.resolve("petstore"))
        val deep = writeDeepRefChain(dir.resolve("deep"), depth = 200)

        RedoclyRuntime.create(EngineMode.ISOLATE).use { runtime ->
            assertEquals(RedoclyRuntime.EffectiveEngine.ISOLATE, runtime.effectiveEngine)
            val redocly = Redocly(runtime)
            val result = redocly.lint(LintOptions(cwd = project.toString(), configPath = project.resolve("redocly.yaml").toString()))
            assertEquals(Totals(errors = 2, warnings = 4), result.totals)
            val deepResult = redocly.lint(LintOptions(cwd = deep.toString(), apis = listOf("openapi.yaml"), extends = listOf("minimal")))
            assertEquals(0, deepResult.totals.errors, deepResult.apis.single().problems.toString())
        }
        RedoclyRuntime.create(EngineMode.AUTO).use { runtime ->
            assertEquals(RedoclyRuntime.EffectiveEngine.ISOLATE, runtime.effectiveEngine)
        }
    }

    @Test
    fun `auto mode falls back to the interpreter without an isolate`() {
        assumeTrue(!RedoclyRuntime.isIsolatePresent() && !Engine.supportsCompilation())
        RedoclyRuntime.create(EngineMode.AUTO).use { runtime ->
            assertEquals(RedoclyRuntime.EffectiveEngine.INTERPRETER, runtime.effectiveEngine)
        }
    }

    @Test
    fun `reports the isolate platform of this jvm`() {
        val platform = RedoclyRuntime.isolatePlatform()
        assertTrue(Regex("(linux|darwin|windows)-(amd64|aarch64)").matches(platform), platform)
    }
}
