package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Scores OpenAPI 3 descriptions for integration simplicity and agent readiness ({@code redocly score}).
 * Fails the build when a score is below {@code minScore}.
 */
@Mojo(name = "score", threadSafe = true)
public final class ScoreMojo extends AbstractApiMojo {

    /** Output format: {@code stylish} or {@code json}. */
    @Parameter(property = "openapi.toolkit.score.format", defaultValue = "stylish")
    String format = "stylish";

    /** Include per-operation details in the stylish output. */
    @Parameter(property = "openapi.toolkit.score.operationDetails", defaultValue = "false")
    boolean operationDetails;

    /** File to write the score report to instead of the build log. Requires a single selected API. */
    @Parameter(property = "openapi.toolkit.score.outputFile")
    File outputFile;

    /** Minimum agent-readiness score, 0 to 100; the build fails when any selected API scores lower. Unset: never fails. */
    @Parameter(property = "openapi.toolkit.score.minScore")
    Double minScore;

    /** Skip this goal only; {@code openapi.toolkit.skip} skips every goal of the plugin. */
    @Parameter(property = "openapi.toolkit.score.skip", defaultValue = "false")
    boolean skipScore;

    @Override
    Goal<?> goal() {
        return new ScoreGoal(this);
    }
}
