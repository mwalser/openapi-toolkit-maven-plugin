package net.mwalser.openapi.toolkit.mojo;

import javax.inject.Inject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.crypto.SettingsDecrypter;

/**
 * Base of all goals: the runtime parameters every goal has.
 *
 * <p>The mojo classes only declare parameters; maven-plugin-plugin takes their descriptions from Javadoc, which
 * is why they are Java. Everything the goals do lives in the Kotlin {@link Goal} hierarchy of this package.
 *
 * <p>Parameters are declared at the level of the hierarchy where they actually take effect, so the generated
 * goal descriptors ({@code mvn help:describe}) only advertise parameters a goal honors:
 * <ul>
 *   <li>{@link AbstractRedoclyMojo}: {@code skip} — every goal</li>
 *   <li>{@link AbstractConfiguredMojo}: {@code configFile}, {@code maxProblems} — goals that read {@code redocly.yaml}</li>
 *   <li>{@link AbstractApiMojo}: {@code apis}, {@code lintConfig} — goals that process API descriptions</li>
 *   <li>{@code extends} only on {@code lint} and {@code bundle}, the goals that evaluate rules</li>
 * </ul>
 */
public abstract class AbstractRedoclyMojo extends AbstractMojo {

    /** The project being built. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    /** The running build; provides the offline flag and the proxy settings. */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    MavenSession session;

    @Inject
    SettingsDecrypter settingsDecrypter;

    /** Skip execution of this goal. */
    @Parameter(property = "openapi.toolkit.skip", defaultValue = "false")
    boolean skip;

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        goal().execute();
    }

    abstract Goal<?> goal();
}
