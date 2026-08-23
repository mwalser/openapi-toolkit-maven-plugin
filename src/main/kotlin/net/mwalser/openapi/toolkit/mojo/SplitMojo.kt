package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.JsPaths
import net.mwalser.openapi.toolkit.redocly.SplitOptions
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File

/**
 * Splits a single-file OpenAPI 3 / AsyncAPI description into a multi-file structure (`redocly split`).
 * Typically run once by hand (`mvn openapi:split -Dopenapi.split.api=... -Dopenapi.split.outputDirectory=...`).
 */
@Mojo(name = "split", threadSafe = true)
class SplitMojo : AbstractRedoclyMojo() {

    /** The description to split (path relative to the project base directory). */
    @Parameter(property = "openapi.split.api", required = true)
    lateinit var api: String

    /** Directory the multi-file structure is written to. */
    @Parameter(property = "openapi.split.outputDirectory", required = true)
    lateinit var outputDirectory: File

    /** Separator used in file names generated from paths, e.g. `/users/{id}` becomes `users_{id}.yaml`. */
    @Parameter(property = "openapi.split.separator", defaultValue = "_")
    var separator: String = "_"

    override fun validateParameters() {
        super.validateParameters()
        if (separator.isBlank()) throw MojoExecutionException("Invalid value for openapi.split.separator; must not be blank")
    }

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun run() {
        val out = resolve(outputDirectory)
        val result = redocly().split(SplitOptions(cwd = jsCwd, api = JsPaths.toJs(api), outDir = JsPaths.toJs(out), separator = separator))
        MavenJsLog.block(log, result.output, MavenJsLog.Level.INFO)
        log.info("Split ${relativize(result.api)} into ${relativize(result.outDir)}")
    }
}
