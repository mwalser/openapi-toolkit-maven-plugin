package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Prints statistics of API descriptions ({@code redocly stats}): references, external documents, schemas,
 * parameters, links, path items, webhooks, operations and tags.
 */
@Mojo(name = "stats", threadSafe = true)
public final class StatsMojo extends AbstractApiMojo {

    /** Output format: {@code stylish}, {@code json} or {@code markdown}. */
    @Parameter(property = "openapi.toolkit.stats.format", defaultValue = "stylish")
    String format = "stylish";

    /** File to write the statistics to instead of the build log. Requires a single selected API. */
    @Parameter(property = "openapi.toolkit.stats.outputFile")
    File outputFile;

    /** Skip this goal only; {@code openapi.toolkit.skip} skips every goal of the plugin. */
    @Parameter(property = "openapi.toolkit.stats.skip", defaultValue = "false")
    boolean skipStats;

    @Override
    Goal<?> goal() {
        return new StatsGoal(this);
    }
}
