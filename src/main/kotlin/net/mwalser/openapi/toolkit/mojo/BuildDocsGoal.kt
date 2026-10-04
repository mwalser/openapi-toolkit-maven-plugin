package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.BuildDocsOptions
import org.apache.maven.plugin.MojoExecutionException
import java.nio.file.Files

internal class BuildDocsGoal(mojo: BuildDocsMojo) : ApiGoal<BuildDocsMojo>(mojo) {

    override val skipGoal get() = SkipParameter("openapi.toolkit.buildDocs.skip", mojo.skipBuildDocs)

    override fun validate() {
        super.validate()
        if (mojo.outputFile != null) requireSingleApi("openapi.toolkit.buildDocs.outputFile", mojo.apis.size)
        mojo.template?.let(::resolve)?.let { template ->
            if (!Files.isRegularFile(template)) throw MojoExecutionException("Template not found: $template (set by openapi.toolkit.buildDocs.template)")
        }
    }

    override fun run() {
        val configFile = resolveConfigFile()
        val outputDirectory = resolve(mojo.outputDirectory)
        val outputFile = mojo.outputFile?.let(::resolve)
        val result = redocly().buildDocs(
            BuildDocsOptions(
                cwd = jsCwd,
                configPath = configFile?.let(::jsPath),
                apis = jsApis,
                outputDirectory = jsPath(outputDirectory),
                outputFile = outputFile?.let(::jsPath),
                title = mojo.title?.takeIf { it.isNotBlank() },
                disableGoogleFont = mojo.disableGoogleFont,
                template = mojo.template?.let { jsPath(resolve(it)) },
                templateOptions = mojo.templateOptions,
                redocOptions = mojo.redocOptions,
                lintConfig = lintConfig,
                maxProblems = maxProblems,
            ),
        )
        reportConfigLint(result.configLint, configFile)
        log.debug("Rendered with Redoc ${result.redocVersion}")
        for (api in result.apis) {
            val source = display(api.path) + api.alias?.let { " using configuration for api '$it'" }.orEmpty()
            val size = Files.size(hostPath(api.outputFile))
            log.info("Created documentation for $source at ${display(api.outputFile)} (${(size + 1023) / 1024} KiB, ${api.durationMillis} ms)")
        }
        val pages = result.apis.map { hostPath(it.outputFile) }
        if (mojo.addResource && pages.isNotEmpty()) addResources(outputFile?.parent ?: outputDirectory, pages, mojo.resourceTargetPath?.takeIf { it.isNotBlank() })
    }
}
