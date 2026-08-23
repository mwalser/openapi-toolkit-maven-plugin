package net.mwalser.openapi.toolkit.redocly

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.copyToRecursively
import kotlin.test.assertEquals

/** Only runs when a polyglot isolate is on the test classpath (`mvn test -Pisolate-tests`). */
class IsolateEngineTest {

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @Test
    fun `lints in a polyglot isolate`(@TempDir dir: Path) {
        assumeTrue(RedoclyRuntime.isIsolateAvailable(), "no js-isolate-*-community artifact on the classpath")
        Path.of("src/test/resources/fixtures/petstore").copyToRecursively(dir, followLinks = false, overwrite = true)

        RedoclyRuntime.create(EngineMode.ISOLATE).use { runtime ->
            assertEquals(RedoclyRuntime.EffectiveEngine.ISOLATE, runtime.effectiveEngine)
            val result = Redocly(runtime).lint(LintOptions(cwd = dir.toString(), configPath = dir.resolve("redocly.yaml").toString()))
            assertEquals(Totals(errors = 2, warnings = 4), result.totals)
        }
        RedoclyRuntime.create(EngineMode.AUTO).use { runtime ->
            assertEquals(RedoclyRuntime.EffectiveEngine.ISOLATE, runtime.effectiveEngine)
        }
    }

    @Test
    fun `auto mode falls back to the interpreter without an isolate`() {
        assumeTrue(!RedoclyRuntime.isIsolateAvailable() && !org.graalvm.polyglot.Engine.supportsCompilation())
        RedoclyRuntime.create(EngineMode.AUTO).use { runtime ->
            assertEquals(RedoclyRuntime.EffectiveEngine.INTERPRETER, runtime.effectiveEngine)
        }
    }
}
