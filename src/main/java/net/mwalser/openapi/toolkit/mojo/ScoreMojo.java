package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Scores OpenAPI 3 descriptions for integration simplicity and agent readiness ({@code redocly score}).
 * Processes every selected API (see {@code apis}); optionally fails the build when an agent-readiness score is
 * below {@code minScore}.
 */
@Mojo(name = "score", threadSafe = true)
public final class ScoreMojo extends AbstractApiMojo {

    /** Output format: {@code stylish} (default) or {@code json}. */
    @Parameter(property = "openapi.score.format", defaultValue = "stylish")
    String format = "stylish";

    /** Include per-operation details in the stylish output. */
    @Parameter(property = "openapi.score.operationDetails", defaultValue = "false")
    boolean operationDetails;

    /** When set, the score output is written to this file instead of the build log. Requires a single selected API. */
    @Parameter(property = "openapi.score.outputFile")
    File outputFile;

    /** Fail the build when the agent-readiness score (0-100) of any selected API is below this value. */
    @Parameter(property = "openapi.score.minScore")
    Double minScore;

    @Override
    Goal<?> goal() {
        return new ScoreGoal(this);
    }
}
