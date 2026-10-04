package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Renders each API description as an HTML reference page with Redoc ({@code redocly build-docs}). The page is
 * pre-rendered and self-contained apart from the Redoc script, which it loads from Redocly's CDN, and the
 * Google Fonts stylesheet.
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

    /** Leave out the link to the Google Fonts stylesheet; the page then renders in the viewer's fallback fonts. */
    @Parameter(property = "openapi.toolkit.buildDocs.disableGoogleFont", defaultValue = "false")
    boolean disableGoogleFont;

    /**
     * Handlebars template of the page instead of the built-in one (or instead of {@code htmlTemplate} from the
     * {@code openapi} section of the configuration file). It receives {@code title}, {@code redocHead} and
     * {@code redocHTML} (both to be inserted unescaped, with triple braces), {@code disableGoogleFont} for the
     * template's own font link, and {@code templateOptions}.
     */
    @Parameter(property = "openapi.toolkit.buildDocs.template")
    File template;

    /** Values for a custom template, available there as {@code templateOptions.name}. */
    @Parameter
    Map<String, String> templateOptions = new LinkedHashMap<>();

    /**
     * Redoc options such as {@code hideDownloadButton} or {@code expandResponses}. They override the options of the
     * {@code openapi} section of the configuration file top-level option by top-level option: a {@code theme}
     * given here replaces the configured {@code theme} as a whole. Values are strings; nested options such as
     * {@code theme} are given as JSON.
     */
    @Parameter
    Map<String, String> redocOptions = new LinkedHashMap<>();

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
