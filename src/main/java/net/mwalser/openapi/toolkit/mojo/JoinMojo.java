package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Joins two or more OpenAPI 3 descriptions into one ({@code redocly join}, experimental upstream).
 * Select the descriptions with the {@code apis} parameter (aliases or paths); at least two are required.
 */
@Mojo(name = "join", threadSafe = true)
public final class JoinMojo extends AbstractApiMojo {

    /** The joined description. The extension ({@code yaml}, {@code yml} or {@code json}) determines the output format. */
    @Parameter(property = "openapi.join.outputFile", defaultValue = "${project.build.directory}/generated-resources/openapi/joined.yaml")
    File outputFile;

    /** Prefix tags with the value of this {@code info} property (e.g. {@code title}) to avoid conflicts. */
    @Parameter(property = "openapi.join.prefixTagsWithInfoProp")
    String prefixTagsWithInfoProp;

    /** Prefix tags with the file name of the description they come from. */
    @Parameter(property = "openapi.join.prefixTagsWithFilename", defaultValue = "false")
    boolean prefixTagsWithFilename;

    /** Prefix component names with the value of this {@code info} property to avoid conflicts. */
    @Parameter(property = "openapi.join.prefixComponentsWithInfoProp")
    String prefixComponentsWithInfoProp;

    /** Do not generate {@code x-tagGroups}. */
    @Parameter(property = "openapi.join.withoutXTagGroups", defaultValue = "false")
    boolean withoutXTagGroups;

    /** Skip this goal only; {@code openapi.skip} skips every goal of the plugin. */
    @Parameter(property = "openapi.join.skip", defaultValue = "false")
    boolean skipJoin;

    @Override
    Goal<?> goal() {
        return new JoinGoal(this);
    }
}
