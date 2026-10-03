package net.mwalser.openapi.toolkit.mojo;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugins.annotations.Parameter;

/** Goals that process API descriptions selected via {@code apis}. */
public abstract class AbstractApiMojo extends AbstractConfiguredMojo {

    /**
     * The API descriptions to process: aliases from the {@code apis} section of the configuration file, paths
     * relative to the project base directory, or URLs. Empty: every API in the configuration file. Without a
     * configuration file, paths or URLs are required and Redocly's built-in {@code recommended} ruleset applies.
     */
    @Parameter(property = "openapi.toolkit.apis")
    List<String> apis = new ArrayList<>();

    /**
     * Severity of problems in the configuration file, which is linted before the goal runs: {@code warn} (reported,
     * invalid entries are ignored), {@code error} (fails the build) or {@code off}.
     */
    @Parameter(property = "openapi.toolkit.lintConfig", defaultValue = "warn")
    String lintConfig = "warn";
}
