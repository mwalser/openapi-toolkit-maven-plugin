package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Splits a single-file OpenAPI 3 / AsyncAPI description into a multi-file structure ({@code redocly split}).
 * Typically run once by hand ({@code mvn openapi:split -Dopenapi.split.api=... -Dopenapi.split.outputDirectory=...}).
 */
@Mojo(name = "split", threadSafe = true)
public final class SplitMojo extends AbstractRedoclyMojo {

    /** The description to split (path relative to the project base directory). */
    @Parameter(property = "openapi.split.api", required = true)
    String api;

    /** Directory the multi-file structure is written to. */
    @Parameter(property = "openapi.split.outputDirectory", required = true)
    File outputDirectory;

    /** Separator used in file names generated from paths, e.g. {@code /users/{id}} becomes {@code users_{id}.yaml}. */
    @Parameter(property = "openapi.split.separator", defaultValue = "_")
    String separator = "_";

    @Override
    Goal<?> goal() {
        return new SplitGoal(this);
    }
}
