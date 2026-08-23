package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import org.apache.maven.plugins.annotations.Parameter;

/** Goals that read the Redocly configuration file. */
public abstract class AbstractConfiguredMojo extends AbstractRedoclyMojo {

    /**
     * The Redocly configuration file. Defaults to {@code redocly.yaml} in the project base directory when that
     * file exists; without a configuration file Redocly's built-in {@code recommended} ruleset is used.
     */
    @Parameter(property = "openapi.configFile")
    File configFile;

    /** Maximum number of problems to print (per API description and for the configuration file). */
    @Parameter(property = "openapi.maxProblems", defaultValue = "100")
    int maxProblems = 100;
}
