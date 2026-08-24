package net.mwalser.openapi.toolkit.mojo;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Lints the Redocly configuration file ({@code redocly check-config}). */
@Mojo(name = "check-config", threadSafe = true)
public final class CheckConfigMojo extends AbstractConfiguredMojo {

    /** Severity of configuration problems: {@code error} (default, fails the build) or {@code warn}. */
    @Parameter(property = "openapi.checkConfig.severity", defaultValue = "error")
    String severity = "error";

    /** Build log output format (see the {@code lint} goal). */
    @Parameter(property = "openapi.checkConfig.format", defaultValue = "stylish")
    String format = "stylish";

    /** Skip this goal only; {@code openapi.skip} skips every goal of the plugin. */
    @Parameter(property = "openapi.checkConfig.skip", defaultValue = "false")
    boolean skipCheckConfig;

    @Override
    Goal<?> goal() {
        return new CheckConfigGoal(this);
    }
}
