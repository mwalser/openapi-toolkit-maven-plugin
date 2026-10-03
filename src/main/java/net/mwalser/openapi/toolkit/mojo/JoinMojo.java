package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Joins two or more OpenAPI 3 descriptions, selected with {@code apis}, into one file ({@code redocly join};
 * experimental upstream).
 */
@Mojo(name = "join", threadSafe = true)
public final class JoinMojo extends AbstractApiMojo {

    /** File to write the joined description to; its extension ({@code yaml}, {@code yml} or {@code json}) sets the format. */
    @Parameter(property = "openapi.toolkit.join.outputFile", defaultValue = "${project.build.directory}/generated-resources/openapi/joined.yaml")
    File outputFile;

    /** Prefix tags with this {@code info} property of their source description, for example {@code title}. Exclusive with {@code prefixTagsWithFilename} and {@code withoutXTagGroups}. */
    @Parameter(property = "openapi.toolkit.join.prefixTagsWithInfoProp")
    String prefixTagsWithInfoProp;

    /** Prefix tags with the file name of their source description. Exclusive with {@code prefixTagsWithInfoProp} and {@code withoutXTagGroups}. */
    @Parameter(property = "openapi.toolkit.join.prefixTagsWithFilename", defaultValue = "false")
    boolean prefixTagsWithFilename;

    /** Prefix component names with this {@code info} property of their source description, for example {@code title}. */
    @Parameter(property = "openapi.toolkit.join.prefixComponentsWithInfoProp")
    String prefixComponentsWithInfoProp;

    /** Do not generate {@code x-tagGroups}. Exclusive with {@code prefixTagsWithInfoProp} and {@code prefixTagsWithFilename}. */
    @Parameter(property = "openapi.toolkit.join.withoutXTagGroups", defaultValue = "false")
    boolean withoutXTagGroups;

    /** Skip this goal only; {@code openapi.toolkit.skip} skips every goal of the plugin. */
    @Parameter(property = "openapi.toolkit.join.skip", defaultValue = "false")
    boolean skipJoin;

    @Override
    Goal<?> goal() {
        return new JoinGoal(this);
    }
}
