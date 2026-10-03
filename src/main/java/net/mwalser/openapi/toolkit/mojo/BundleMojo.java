package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import java.util.List;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Bundles each API description into a single file ({@code redocly bundle}): resolves {@code $ref}s to other files
 * and applies the decorators configured in {@code redocly.yaml}.
 */
@Mojo(name = "bundle", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public final class BundleMojo extends AbstractApiMojo {

    @Inject
    MavenProjectHelper projectHelper;

    /** Replaces the {@code extends} list of the configuration. Built-in rulesets: {@code minimal}, {@code recommended}, {@code recommended-strict}, {@code spec}. */
    @Parameter(property = "openapi.toolkit.extends", alias = "extends")
    List<String> extendsRulesets;

    /** Directory the bundles are written to, as {@code <alias>.<ext>} (APIs from the configuration file) or {@code <basename>.<ext>} (paths). */
    @Parameter(property = "openapi.toolkit.bundle.outputDirectory", defaultValue = "${project.build.directory}/generated-resources/openapi")
    File outputDirectory;

    /** File to write the bundle to instead of {@code outputDirectory}. Requires a single selected API. */
    @Parameter(property = "openapi.toolkit.bundle.outputFile")
    File outputFile;

    /**
     * Output format and file extension: {@code yaml}, {@code yml} or {@code json}. Unset: the extension of
     * {@code outputFile}, else the input's format (JSON stays JSON), else {@code yaml}.
     */
    @Parameter(property = "openapi.toolkit.bundle.ext")
    String ext;

    /** Produce a fully dereferenced bundle (no {@code $ref} left). */
    @Parameter(property = "openapi.toolkit.bundle.dereferenced", defaultValue = "false")
    boolean dereferenced;

    /** Write the bundle even when errors were encountered. */
    @Parameter(property = "openapi.toolkit.bundle.force", defaultValue = "false")
    boolean force;

    /** Remove components that are not referenced anywhere. */
    @Parameter(property = "openapi.toolkit.bundle.removeUnusedComponents", defaultValue = "false")
    boolean removeUnusedComponents;

    /** Keep absolute URL {@code $ref}s instead of inlining them. */
    @Parameter(property = "openapi.toolkit.bundle.keepUrlReferences", defaultValue = "false")
    boolean keepUrlReferences;

    /**
     * Naming of components pulled in from other files: {@code basename} (file name; Redocly's default when unset)
     * or {@code title} (the schema's {@code title}).
     */
    @Parameter(property = "openapi.toolkit.bundle.componentNamesStrategy")
    String componentNamesStrategy;

    /** Severity of component name conflicts between files: {@code warn} (Redocly's default when unset), {@code error} or {@code off}. */
    @Parameter(property = "openapi.toolkit.bundle.componentRenamingConflicts")
    String componentRenamingConflicts;

    /** Decorators not to apply, by decorator id. */
    @Parameter(property = "openapi.toolkit.bundle.skipDecorators")
    List<String> skipDecorators;

    /**
     * Add the bundles as project resources; they are packaged at the root of the JAR under their file names.
     * The goal must run before {@code process-resources}, which copies resources into the JAR.
     */
    @Parameter(property = "openapi.toolkit.bundle.addResource", defaultValue = "false")
    boolean addResource;

    /**
     * Attach each bundle as a build artifact other modules can depend on: type is the output extension,
     * classifier is the alias, or {@code classifier} for inputs without one. Aliases must be distinct per type.
     */
    @Parameter(property = "openapi.toolkit.bundle.attach", defaultValue = "false")
    boolean attach;

    /** Classifier of attached bundles whose input has no alias. */
    @Parameter(property = "openapi.toolkit.bundle.classifier", defaultValue = "openapi")
    String classifier = "openapi";

    /** Skip this goal only; {@code openapi.toolkit.skip} skips every goal of the plugin. */
    @Parameter(property = "openapi.toolkit.bundle.skip", defaultValue = "false")
    boolean skipBundle;

    @Override
    Goal<?> goal() {
        return new BundleGoal(this);
    }
}
