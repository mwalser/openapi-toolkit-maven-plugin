package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.JoinOptions
import org.apache.maven.plugin.MojoExecutionException

internal class JoinGoal(mojo: JoinMojo) : ApiGoal<JoinMojo>(mojo) {

    override val skipGoal get() = SkipParameter("openapi.join.skip", mojo.skipJoin)

    override fun validate() {
        super.validate()
        if (extension(mojo.outputFile) !in EXTENSIONS) {
            throw MojoExecutionException("Invalid extension of openapi.join.outputFile '${mojo.outputFile.name}'; expected one of: ${EXTENSIONS.joinToString(", ")}")
        }
        // handleJoin accepts at most one of the three tag-shaping options; failing here names the properties before anything runs
        val tagOptions = buildList {
            if (mojo.prefixTagsWithInfoProp != null) add("openapi.join.prefixTagsWithInfoProp")
            if (mojo.prefixTagsWithFilename) add("openapi.join.prefixTagsWithFilename")
            if (mojo.withoutXTagGroups) add("openapi.join.withoutXTagGroups")
        }
        if (tagOptions.size > 1) {
            throw MojoExecutionException("${tagOptions.joinToString(" and ")} cannot be used together; set at most one of them.")
        }
    }

    override fun run() {
        val configFile = resolveConfigFile()
        val result = redocly().join(
            JoinOptions(
                cwd = jsCwd,
                configPath = configFile?.let(::jsPath),
                apis = jsApis,
                output = jsPath(resolve(mojo.outputFile)),
                prefixTagsWithInfoProp = mojo.prefixTagsWithInfoProp,
                prefixTagsWithFilename = mojo.prefixTagsWithFilename,
                prefixComponentsWithInfoProp = mojo.prefixComponentsWithInfoProp,
                withoutXTagGroups = mojo.withoutXTagGroups,
                lintConfig = lintConfig,
                maxProblems = maxProblems,
            ),
        )
        reportConfigLint(result.configLint, configFile)
        MavenJsLog.block(log, result.output, MavenJsLog.Level.INFO)
        val inputs = result.apis.joinToString(", ") { display(it.path) }
        log.info("Joined ${plural(result.apis.size, "API description")} ($inputs) into ${display(result.outputFile)}")
    }
}
