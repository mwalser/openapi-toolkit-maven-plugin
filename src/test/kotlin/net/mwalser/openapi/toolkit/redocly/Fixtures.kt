package net.mwalser.openapi.toolkit.redocly

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively

/** Copies `src/test/resources/fixtures/<name>` into [target] and returns it. */
@OptIn(ExperimentalPathApi::class)
fun fixture(name: String, target: Path): Path {
    Path.of("src/test/resources/fixtures", name).copyToRecursively(target, followLinks = false, overwrite = true)
    return target
}

/** Writes a minimal OpenAPI description whose only schema is a `$ref` to [ref]. */
fun writeApiWithRef(dir: Path, ref: String): Path = Files.writeString(
    dir.resolve("openapi.yaml"),
    """
    openapi: 3.0.3
    info: { title: Test, version: '1' }
    servers: [{ url: https://api.example.test }]
    paths: {}
    components:
      schemas:
        Root:
          ${'$'}ref: '$ref'
    """.trimIndent(),
)

/** Writes `openapi.yaml` referencing a chain of [depth] schema files, each `$ref`erencing the next; returns [dir]. */
fun writeDeepRefChain(dir: Path, depth: Int): Path {
    Files.createDirectories(dir.resolve("schemas"))
    for (i in 0..<depth) {
        Files.writeString(dir.resolve("schemas/$i.yaml"), "type: object\nproperties:\n  next:\n    \$ref: './${i + 1}.yaml'\n")
    }
    Files.writeString(dir.resolve("schemas/$depth.yaml"), "type: string\n")
    writeApiWithRef(dir, "./schemas/0.yaml")
    return dir
}

/** The path as the JavaScript side expects it; tests hand paths to [Redocly] the way the goals do (see [JsPaths]). */
val Path.js: String get() = JsPaths.toJs(this)

/** A path reported by the JavaScript side, as a host path. */
fun hostPath(jsPath: String): Path = JsPaths.toHostPath(jsPath)
