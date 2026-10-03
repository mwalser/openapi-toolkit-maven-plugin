package net.mwalser.openapi.toolkit.mojo;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Lints the Redocly configuration file ({@code redocly check-config}). */
@Mojo(name = "check-config", threadSafe = true)
public final class CheckConfigMojo extends AbstractConfiguredMojo {

    /** Severity of configuration problems: {@code error} (fails the build) or {@code warn}. */
    @Parameter(property = "openapi.toolkit.checkConfig.severity", defaultValue = "error")
    String severity = "error";

    /** Format of the problems in the build log: {@code stylish}, {@code codeframe}, {@code summary}, {@code markdown} or {@code github-actions}. */
    @Parameter(property = "openapi.toolkit.checkConfig.format", defaultValue = "stylish")
    String format = "stylish";

    /** Skip this goal only; {@code openapi.toolkit.skip} skips every goal of the plugin. */
    @Parameter(property = "openapi.toolkit.checkConfig.skip", defaultValue = "false")
    boolean skipCheckConfig;

    @Override
    Goal<?> goal() {
        return new CheckConfigGoal(this);
    }
}
