package net.mwalser.openapi.toolkit.mojo;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugins.annotations.Parameter;

/** Goals that process API descriptions selected via {@code apis}. */
public abstract class AbstractApiMojo extends AbstractConfiguredMojo {

    /**
     * The API descriptions to process, each either an alias from the {@code apis} section of the configuration
     * file or a path (relative to the project base directory) or URL. When empty, all APIs defined in the
     * configuration file are processed.
     */
    @Parameter(property = "openapi.toolkit.apis")
    List<String> apis = new ArrayList<>();

    /** Severity used when linting the configuration file itself before the command runs: {@code warn}, {@code error} or {@code off}. */
    @Parameter(property = "openapi.toolkit.lintConfig", defaultValue = "warn")
    String lintConfig = "warn";
}
