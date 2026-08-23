package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.JoinOptions
import net.mwalser.openapi.toolkit.redocly.JsPaths
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File

/**
 * Joins two or more OpenAPI 3 descriptions into one (`redocly join`, experimental upstream).
 * Select the descriptions with the `apis` parameter (aliases or paths); at least two are required.
 */
@Mojo(name = "join", threadSafe = true)
class JoinMojo : AbstractRedoclyMojo() {

    /** The joined description. The extension (`yaml`, `yml` or `json`) determines the output format. */
    @Parameter(property = "openapi.join.outputFile", defaultValue = "\${project.build.directory}/generated-resources/openapi/joined.yaml")
    lateinit var outputFile: File

    /** Prefix tags with the value of this `info` property (e.g. `title`) to avoid conflicts. */
    @Parameter(property = "openapi.join.prefixTagsWithInfoProp")
    var prefixTagsWithInfoProp: String? = null

    /** Prefix tags with the file name of the description they come from. */
    @Parameter(property = "openapi.join.prefixTagsWithFilename", defaultValue = "false")
    var prefixTagsWithFilename: Boolean = false

    /** Prefix component names with the value of this `info` property to avoid conflicts. */
    @Parameter(property = "openapi.join.prefixComponentsWithInfoProp")
    var prefixComponentsWithInfoProp: String? = null

    /** Do not generate `x-tagGroups`. */
    @Parameter(property = "openapi.join.withoutXTagGroups", defaultValue = "false")
    var withoutXTagGroups: Boolean = false

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun run() {
        val configPath = resolveConfigFile()
        val out = if (outputFile.isAbsolute) outputFile else File(project.basedir, outputFile.path)
        val result = redocly().join(
            JoinOptions(
                cwd = jsCwd,
                configPath = configPath?.let { jsPath(it) },
                apis = jsApis,
                output = JsPaths.toJs(out),
                prefixTagsWithInfoProp = prefixTagsWithInfoProp,
                prefixTagsWithFilename = prefixTagsWithFilename,
                prefixComponentsWithInfoProp = prefixComponentsWithInfoProp,
                withoutXTagGroups = withoutXTagGroups,
                lintConfig = lintConfig,
            ),
        )
        reportConfigLint(result.configLint, configPath)
        MavenJsLog.block(log, result.output, MavenJsLog.Level.INFO)
        log.info("Joined ${plural(apis.size, "API description")} into ${relativize(result.outputFile)}")
    }
}
