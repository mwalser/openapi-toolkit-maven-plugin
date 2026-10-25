package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Renders each API description (OpenAPI, Swagger 2, AsyncAPI or GraphQL) as an HTML reference page with Redoc 3
 * ({@code redocly build-docs}), after bundling it with the configured decorators and preprocessors. The page is
 * pre-rendered and loads the Redoc script from Redocly's CDN unless {@code inlineBundle} embeds it. Unlike the
 * Redocly CLI, the pages send Redoc's telemetry from readers' browsers only when {@code telemetry} is set.
 */
@Mojo(name = "build-docs", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public final class BuildDocsMojo extends AbstractApiMojo {

    /** Directory the pages are written to, named after the alias (APIs from the configuration file) or the file name (paths), with extension {@code html}. */
    @Parameter(property = "openapi.toolkit.buildDocs.outputDirectory", defaultValue = "${project.build.directory}/generated-resources/redoc")
    File outputDirectory;

    /** File to write the page to instead of {@code outputDirectory}. Requires a single selected API. */
    @Parameter(property = "openapi.toolkit.buildDocs.outputFile")
    File outputFile;

    /** Title of the pages; with several APIs they all get it. Unset: the title of each API description. */
    @Parameter(property = "openapi.toolkit.buildDocs.title")
    String title;

    /**
     * Handlebars template of the page instead of the built-in one (or instead of {@code htmlTemplate} from the
     * configuration file section of the description's type: {@code openapi}, {@code asyncapi} or {@code graphql}).
     * It receives {@code title}, {@code redocHead} and {@code redocHTML} (both to be inserted unescaped, with triple
     * braces), and {@code templateOptions}.
     */
    @Parameter(property = "openapi.toolkit.buildDocs.template")
    File template;

    /** Values for a custom template, available there as {@code templateOptions.name}. */
    @Parameter
    Map<String, String> templateOptions = new LinkedHashMap<>();

    /**
     * Redoc options such as {@code hideDownloadButtons} or {@code jsonSamplesDepth}. They override the options of
     * the configuration file section that matches the description's type ({@code openapi}, {@code asyncapi} or
     * {@code graphql}) top-level option by top-level option: a nested option given here replaces the configured one
     * as a whole. Values are strings; nested options are given as JSON.
     */
    @Parameter
    Map<String, String> redocOptions = new LinkedHashMap<>();

    /** Embed the Redoc script (about 2.2 MB) in each page instead of loading it from Redocly's CDN. */
    @Parameter(property = "openapi.toolkit.buildDocs.inlineBundle", defaultValue = "false")
    boolean inlineBundle;

    /**
     * Leave out the "API docs by Redocly" link at the foot of the sidebar, whose logo the page otherwise loads from
     * Redocly's CDN; with {@code inlineBundle}, the page then makes no requests of its own. Requires
     * {@code inlineBundle}, since the Redoc script from the CDN would add the link back.
     */
    @Parameter(property = "openapi.toolkit.buildDocs.removeRedoclyLogo", defaultValue = "false")
    boolean removeRedoclyLogo;

    /**
     * Let the pages send <a href="https://redocly.com/docs/redoc/telemetry">Redoc telemetry</a> from readers'
     * browsers. {@code telemetry: off} in the configuration file or the environment variable
     * {@code REDOCLY_TELEMETRY=off} keep it off even when this is set.
     */
    @Parameter(property = "openapi.toolkit.buildDocs.telemetry", defaultValue = "false")
    boolean telemetry;

    /**
     * Add the pages as project resources, packaged under their file names at the root of the JAR or under
     * {@code resourceTargetPath}. The goal must run before {@code process-resources}, which copies resources into the JAR.
     */
    @Parameter(property = "openapi.toolkit.buildDocs.addResource", defaultValue = "false")
    boolean addResource;

    /**
     * Directory inside the JAR for {@code addResource}, relative to its root: {@code META-INF/resources} for a
     * page that Spring Boot, Quarkus and servlet containers serve as a static file. Unset: the root of the JAR.
     */
    @Parameter(property = "openapi.toolkit.buildDocs.resourceTargetPath")
    String resourceTargetPath;

    /** Skip this goal only; {@code openapi.toolkit.skip} skips every goal of the plugin. */
    @Parameter(property = "openapi.toolkit.buildDocs.skip", defaultValue = "false")
    boolean skipBuildDocs;

    @Override
    Goal<?> goal() {
        return new BuildDocsGoal(this);
    }
}
