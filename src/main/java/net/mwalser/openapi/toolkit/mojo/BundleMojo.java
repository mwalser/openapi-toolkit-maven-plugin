package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import java.util.List;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Bundles multi-file API descriptions into single files ({@code redocly bundle}), applying the decorators
 * configured in {@code redocly.yaml}.
 */
@Mojo(name = "bundle", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public final class BundleMojo extends AbstractApiMojo {

    @Inject
    MavenProjectHelper projectHelper;

    /** Overrides the {@code extends} list of the configuration, e.g. {@code recommended}, {@code minimal}, {@code recommended-strict}. */
    @Parameter(property = "openapi.extends", alias = "extends")
    List<String> extendsRulesets;

    /** Directory the bundled files are written to. File names are {@code <alias>.<ext>} or {@code <basename>.<ext>}. */
    @Parameter(property = "openapi.bundle.outputDirectory", defaultValue = "${project.build.directory}/generated-resources/openapi")
    File outputDirectory;

    /** Write the bundle to this file instead of {@code outputDirectory}. Only allowed when a single API is bundled. */
    @Parameter(property = "openapi.bundle.outputFile")
    File outputFile;

    /**
     * Output format: {@code yaml}, {@code yml} or {@code json}. Defaults to the extension of {@code outputFile}
     * when that is set, otherwise {@code yaml}.
     */
    @Parameter(property = "openapi.bundle.ext")
    String ext;

    /** Produce a fully dereferenced bundle (no {@code $ref} left). */
    @Parameter(property = "openapi.bundle.dereferenced", defaultValue = "false")
    boolean dereferenced;

    /** Write the bundle even when errors were encountered. */
    @Parameter(property = "openapi.bundle.force", defaultValue = "false")
    boolean force;

    /** Remove components that are not referenced anywhere. */
    @Parameter(property = "openapi.bundle.removeUnusedComponents", defaultValue = "false")
    boolean removeUnusedComponents;

    /** Keep absolute URL {@code $ref}s instead of inlining them. */
    @Parameter(property = "openapi.bundle.keepUrlReferences", defaultValue = "false")
    boolean keepUrlReferences;

    /**
     * How components pulled in from other files are named: {@code basename} (Redocly's default, from the file name)
     * or {@code title} (from the schema's {@code title}).
     */
    @Parameter(property = "openapi.bundle.componentNamesStrategy")
    String componentNamesStrategy;

    /** Severity of component naming conflicts between files: {@code warn} (Redocly's default), {@code error} or {@code off}. */
    @Parameter(property = "openapi.bundle.componentRenamingConflicts")
    String componentRenamingConflicts;

    /** Decorator ids to skip. */
    @Parameter(property = "openapi.bundle.skipDecorators")
    List<String> skipDecorators;

    /** Add the written bundles as project resources so they end up in the artifact. */
    @Parameter(property = "openapi.bundle.addResource", defaultValue = "false")
    boolean addResource;

    /**
     * Attach each bundle as an additional build artifact (type = {@code ext}, classifier = alias or {@code classifier}),
     * so other modules can depend on it.
     */
    @Parameter(property = "openapi.bundle.attach", defaultValue = "false")
    boolean attach;

    /** Classifier used when attaching a bundle without an alias. Defaults to {@code openapi}. */
    @Parameter(property = "openapi.bundle.classifier", defaultValue = "openapi")
    String classifier = "openapi";

    @Override
    Goal<?> goal() {
        return new BundleGoal(this);
    }
}
