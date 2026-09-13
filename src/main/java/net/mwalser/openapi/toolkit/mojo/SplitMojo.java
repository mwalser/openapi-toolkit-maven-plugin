package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Splits a single-file OpenAPI 3 / AsyncAPI description into a multi-file structure ({@code redocly split}).
 * Typically run once by hand ({@code mvn openapi-toolkit:split -Dopenapi.toolkit.split.api=... -Dopenapi.toolkit.split.outputDirectory=...}).
 */
@Mojo(name = "split", threadSafe = true)
public final class SplitMojo extends AbstractRedoclyMojo {

    /** The description to split (path relative to the project base directory). */
    @Parameter(property = "openapi.toolkit.split.api", required = true)
    String api;

    /** Directory the multi-file structure is written to. */
    @Parameter(property = "openapi.toolkit.split.outputDirectory", required = true)
    File outputDirectory;

    /** Separator used in file names generated from paths, e.g. {@code /users/{id}} becomes {@code users_{id}.yaml}. */
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
