package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Splits a single-file OpenAPI 3 or AsyncAPI description into a multi-file tree ({@code redocly split}).
 * Usually run once from the command line; does not read {@code redocly.yaml}.
 */
@Mojo(name = "split", threadSafe = true)
public final class SplitMojo extends AbstractRedoclyMojo {

    /** The description to split: a path relative to the project base directory. */
    @Parameter(property = "openapi.toolkit.split.api", required = true)
    String api;

    /** Directory the file tree is written to. */
    @Parameter(property = "openapi.toolkit.split.outputDirectory", required = true)
    File outputDirectory;

    /** Separator in file names generated from paths: with the default, <code>/users/{id}</code> becomes <code>users_{id}.yaml</code>. */
    @Parameter(property = "openapi.toolkit.split.separator", defaultValue = "_")
    String separator = "_";

    /** Skip this goal only; {@code openapi.toolkit.skip} skips every goal of the plugin. */
    @Parameter(property = "openapi.toolkit.split.skip", defaultValue = "false")
    boolean skipSplit;

    @Override
    Goal<?> goal() {
        return new SplitGoal(this);
    }
}
