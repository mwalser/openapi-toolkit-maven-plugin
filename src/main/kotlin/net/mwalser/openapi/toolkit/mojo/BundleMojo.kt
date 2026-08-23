package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.BundleOptions
import net.mwalser.openapi.toolkit.redocly.JsPaths
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProjectHelper
import java.io.File

/**
 * Bundles multi-file API descriptions into single files (`redocly bundle`), applying the decorators
 * configured in `redocly.yaml`.
 */
@Mojo(name = "bundle", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
class BundleMojo : AbstractApiMojo() {

    @Component
    lateinit var projectHelper: MavenProjectHelper

    /** Overrides the `extends` list of the configuration, e.g. `recommended`, `minimal`, `recommended-strict`. */
    @Parameter(property = "openapi.extends")
    var extends: List<String>? = null

    /** Directory the bundled files are written to. File names are `<alias>.<ext>` or `<basename>.<ext>`. */
    @Parameter(property = "openapi.bundle.outputDirectory", defaultValue = "\${project.build.directory}/generated-resources/openapi")
    lateinit var outputDirectory: File

    /** Write the bundle to this file instead of `outputDirectory`. Only allowed when a single API is bundled. */
    @Parameter(property = "openapi.bundle.outputFile")
    var outputFile: File? = null

    /** Output format: `yaml` (default), `yml` or `json`. */
    @Parameter(property = "openapi.bundle.ext", defaultValue = "yaml")
    var ext: String = "yaml"

    /** Produce a fully dereferenced bundle (no `$ref` left). */
    @Parameter(property = "openapi.bundle.dereferenced", defaultValue = "false")
    var dereferenced: Boolean = false

    /** Write the bundle even when errors were encountered. */
    @Parameter(property = "openapi.bundle.force", defaultValue = "false")
    var force: Boolean = false

    /** Remove components that are not referenced anywhere. */
    @Parameter(property = "openapi.bundle.removeUnusedComponents", defaultValue = "false")
    var removeUnusedComponents: Boolean = false

    /** Keep absolute URL `$ref`s instead of inlining them. */
    @Parameter(property = "openapi.bundle.keepUrlReferences", defaultValue = "false")
    var keepUrlReferences: Boolean = false

    /** Naming strategy for components pulled in from other files: `auto` (default), `prefix-tag`, `prefix-file`, ... */
    @Parameter(property = "openapi.bundle.componentNamesStrategy")
    var componentNamesStrategy: String? = null

    /** Severity of component naming conflicts between files: `warn` (Redocly's default), `error` or `off`. */
    @Parameter(property = "openapi.bundle.componentRenamingConflicts")
    var componentRenamingConflicts: String? = null

    /** Decorator ids to skip. */
    @Parameter(property = "openapi.bundle.skipDecorators")
    var skipDecorators: List<String>? = null

    /** Preprocessor ids to skip. */
    @Parameter(property = "openapi.bundle.skipPreprocessors")
    var skipPreprocessors: List<String>? = null

    /** Add `outputDirectory` as a resource directory of the project so the bundles end up in the artifact. */
    @Parameter(property = "openapi.bundle.addResource", defaultValue = "false")
    var addResource: Boolean = false

    /**
     * Attach each bundle as an additional build artifact (type = `ext`, classifier = alias or `classifier`),
     * so other modules can depend on it.
     */
    @Parameter(property = "openapi.bundle.attach", defaultValue = "false")
    var attach: Boolean = false

    /** Classifier used when attaching a bundle without an alias. Defaults to `openapi`. */
    @Parameter(property = "openapi.bundle.classifier", defaultValue = "openapi")
    var classifier: String = "openapi"

    override fun validateParameters() {
        super.validateParameters()
        requireOneOf("openapi.bundle.ext", ext, listOf("yaml", "yml", "json"))
        componentRenamingConflicts?.let { requireOneOf("openapi.bundle.componentRenamingConflicts", it, CONFIG_LINT_SEVERITIES) }
        if (attach && classifier.isBlank()) throw MojoExecutionException("openapi.bundle.classifier must not be blank when attach=true")
    }

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun run() {
        val configPath = resolveConfigFile()
        val outDir = resolve(outputDirectory)
        val outFile = outputFile?.let { resolve(it) }
        val result = redocly().bundle(
            BundleOptions(
                cwd = jsCwd,
                configPath = configPath?.let { jsPath(it) },
                apis = jsApis,
                extends = extends,
                outputDirectory = JsPaths.toJs(outDir),
                outputFile = outFile?.let { JsPaths.toJs(it) },
                ext = ext,
                dereferenced = dereferenced,
                force = force,
                removeUnusedComponents = removeUnusedComponents,
                keepUrlReferences = keepUrlReferences,
                componentNamesStrategy = componentNamesStrategy,
                componentRenamingConflicts = componentRenamingConflicts,
                skipDecorators = skipDecorators,
                skipPreprocessors = skipPreprocessors,
                lintConfig = lintConfig,
                maxProblems = maxProblems,
            ),
        )

        reportConfigLint(result.configLint, configPath)

        for (api in result.apis) {
            val using = api.alias?.let { " using configuration for api '$it'" } ?: ""
            MavenJsLog.block(log, api.output, levelFor(api.totals))
            when {
                api.written && api.totals.errors > 0 ->
                    log.warn("Created bundle for ${relativize(api.path)}$using at ${relativize(api.outputFile)} with ${plural(api.totals.errors, "error")} (ignored because of force=true)")
                api.written ->
                    log.info("Created bundle for ${relativize(api.path)}$using at ${relativize(api.outputFile)} (${api.durationMillis} ms)")
                else ->
                    log.error("Errors encountered while bundling ${relativize(api.path)}: bundle not created (use force=true to ignore errors)")
            }
            if (api.removedComponents > 0) log.info("Removed ${plural(api.removedComponents, "unused component")}")
            if (api.written && attach) {
                val artifactClassifier = api.alias ?: classifier
                projectHelper.attachArtifact(project, ext, artifactClassifier, File(hostPath(api.outputFile)))
                log.info("Attached ${relativize(api.outputFile)} as artifact (type=$ext, classifier=$artifactClassifier)")
            }
        }

        if (addResource && result.apis.any { it.written }) {
            val resourceDir = outFile?.parentFile ?: outDir
            projectHelper.addResource(project, resourceDir.path, emptyList(), emptyList())
            log.info("Added ${relativize(resourceDir.path)} as resource directory")
        }
        reportUnused(result.unused, configPath)

        if (result.totals.errors > 0 && !force) {
            throw MojoFailureException("OpenAPI bundle failed with ${plural(result.totals.errors, "error")}.")
        }
    }
}
