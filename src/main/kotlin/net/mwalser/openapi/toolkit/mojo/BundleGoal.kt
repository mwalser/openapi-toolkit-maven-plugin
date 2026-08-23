package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.ApiBundleResult
import net.mwalser.openapi.toolkit.redocly.BundleOptions
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import java.io.File
import java.nio.file.Path

internal class BundleGoal(mojo: BundleMojo) : ApiGoal<BundleMojo>(mojo) {

    /** The output format actually used. */
    private val ext: String get() = mojo.ext ?: mojo.outputFile?.let(::extension) ?: "yaml"

    override fun validate() {
        super.validate()
        mojo.ext?.let { requireOneOf("openapi.bundle.ext", it, EXTENSIONS) }
        mojo.componentNamesStrategy?.let { requireOneOf("openapi.bundle.componentNamesStrategy", it, listOf("basename", "title")) }
        mojo.componentRenamingConflicts?.let { requireOneOf("openapi.bundle.componentRenamingConflicts", it, SEVERITIES) }
        if (mojo.attach && mojo.classifier.isBlank()) throw MojoExecutionException("openapi.bundle.classifier must not be blank when attach=true")
        mojo.outputFile?.let { outputFile ->
            val fileExt = extension(outputFile)
            if (fileExt !in EXTENSIONS) {
                throw MojoExecutionException("Invalid extension of openapi.bundle.outputFile '${outputFile.name}'; expected one of: ${EXTENSIONS.joinToString(", ")}")
            }
            if (mojo.ext != null && mojo.ext != fileExt) {
                throw MojoExecutionException("openapi.bundle.ext '${mojo.ext}' conflicts with the extension of openapi.bundle.outputFile '${outputFile.name}'")
            }
        }
    }

    override fun run() {
        val configFile = resolveConfigFile()
        val outputDirectory = resolve(mojo.outputDirectory)
        val outputFile = mojo.outputFile?.let(::resolve)
        val result = redocly().bundle(
            BundleOptions(
                cwd = jsCwd,
                configPath = configFile?.let(::jsPath),
                apis = jsApis,
                extends = mojo.extendsRulesets,
                outputDirectory = jsPath(outputDirectory),
                outputFile = outputFile?.let(::jsPath),
                ext = ext,
                dereferenced = mojo.dereferenced,
                force = mojo.force,
                removeUnusedComponents = mojo.removeUnusedComponents,
                keepUrlReferences = mojo.keepUrlReferences,
                componentNamesStrategy = mojo.componentNamesStrategy,
                componentRenamingConflicts = mojo.componentRenamingConflicts,
                skipDecorators = mojo.skipDecorators,
                lintConfig = lintConfig,
                maxProblems = maxProblems,
            ),
        )

        reportConfigLint(result.configLint, configFile)
        if (mojo.attach && result.apis.count { it.alias == null } > 1) {
            throw MojoExecutionException(
                "openapi.bundle.attach cannot attach more than one API without an alias because classifier '${mojo.classifier}' would collide; " +
                    "assign aliases in redocly.yaml or select one API.",
            )
        }
        for (api in result.apis) {
            report(api)
            if (api.written && mojo.attach) attach(api)
        }

        val written = result.apis.filter { it.written }.map { hostPath(it.outputFile) }
        if (mojo.addResource && written.isNotEmpty()) addResources(outputFile?.parent ?: outputDirectory, written)
        reportUnused(result.unused, configFile)

        if (result.totals.errors > 0 && !mojo.force) {
            throw MojoFailureException("OpenAPI bundle failed with ${plural(result.totals.errors, "error")}.")
        }
    }

    private fun report(api: ApiBundleResult) {
        MavenJsLog.block(log, api.output, levelFor(api.totals))
        val source = display(api.path) + api.alias?.let { " using configuration for api '$it'" }.orEmpty()
        val errors = api.totals.errors
        when {
            !api.written -> log.error("Errors encountered while bundling $source: bundle not created (use force=true to ignore errors)")
            errors > 0 -> log.warn("Created bundle for $source at ${display(api.outputFile)} with ${plural(errors, "error")} (ignored because of force=true)")
            else -> log.info("Created bundle for $source at ${display(api.outputFile)} (${api.durationMillis} ms)")
        }
        if (api.removedComponents > 0) log.info("Removed ${plural(api.removedComponents, "unused component")}")
    }

    private fun attach(api: ApiBundleResult) {
        val classifier = api.alias ?: mojo.classifier
        mojo.projectHelper.attachArtifact(mojo.project, ext, classifier, hostPath(api.outputFile).toFile())
        log.info("Attached ${display(api.outputFile)} as artifact (type=$ext, classifier=$classifier)")
    }

    /** Adds only the written bundles, not everything in [directory] (which may be the module itself). */
    private fun addResources(directory: Path, files: List<Path>) {
        val includes = files.map { directory.relativize(it).toString() }
        mojo.projectHelper.addResource(mojo.project, directory.toString(), includes, emptyList<String>())
        log.info("Added ${includes.joinToString(", ")} in ${display(directory)} as resources")
    }

    companion object {
        val EXTENSIONS: List<String> = listOf("yaml", "yml", "json")

        /** The lower-cased extension of a file name, or an empty string. */
        fun extension(file: File): String = file.extension.lowercase()
    }
}
