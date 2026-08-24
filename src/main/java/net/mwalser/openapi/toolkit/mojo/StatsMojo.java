package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Prints statistics about API descriptions ({@code redocly stats}): number of paths, operations, schemas, ...
 * Processes every selected API (see {@code apis}).
 */
@Mojo(name = "stats", threadSafe = true)
public final class StatsMojo extends AbstractApiMojo {

    /** Output format: {@code stylish} (default), {@code json} or {@code markdown}. */
    @Parameter(property = "openapi.stats.format", defaultValue = "stylish")
    String format = "stylish";

    /** When set, the statistics are written to this file instead of the build log. Requires a single selected API. */
    @Parameter(property = "openapi.stats.outputFile")
    File outputFile;

    /** Skip this goal only; {@code openapi.skip} skips every goal of the plugin. */
    @Parameter(property = "openapi.stats.skip", defaultValue = "false")
    boolean skipStats;

    @Override
    Goal<?> goal() {
        return new StatsGoal(this);
    }
}
