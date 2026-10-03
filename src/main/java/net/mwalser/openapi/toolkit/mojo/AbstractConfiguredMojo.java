package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import org.apache.maven.plugins.annotations.Parameter;

/** Goals that read the Redocly configuration file. */
public abstract class AbstractConfiguredMojo extends AbstractRedoclyMojo {

    /**
     * The Redocly configuration file. Unset: {@code redocly.yaml} in the project base directory if it exists,
     * otherwise no configuration file.
     */
    @Parameter(property = "openapi.toolkit.configFile")
    File configFile;

    /** Maximum number of problems printed to the build log, per API description and for the configuration file. Report files are not limited. */
    @Parameter(property = "openapi.toolkit.maxProblems", defaultValue = "100")
    int maxProblems = 100;
}
