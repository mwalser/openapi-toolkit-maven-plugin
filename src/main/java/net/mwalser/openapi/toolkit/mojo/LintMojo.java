package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Lints API descriptions with Redocly ({@code redocly lint}). Fails the build when problems with severity
 * {@code error} are found (configurable via {@code failOnErrors} / {@code failOnWarnings}).
 */
@Mojo(name = "lint", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class LintMojo extends AbstractApiMojo {

    /** Overrides the {@code extends} list of the configuration, e.g. {@code recommended}, {@code minimal}, {@code recommended-strict}. */
    @Parameter(property = "openapi.extends", alias = "extends")
    List<String> extendsRulesets;

    /**
     * Build log output format: {@code stylish} (default), {@code codeframe}, {@code summary}, {@code markdown} or
     * {@code github-actions} (annotations, printed unprefixed so GitHub picks them up). Machine-readable formats
     * belong in {@code reportFile}.
     */
    @Parameter(property = "openapi.lint.format", defaultValue = "stylish")
    String format = "stylish";

    /** When set, all problems (not limited by {@code maxProblems}) are additionally written to this file in {@code reportFormat}. */
    @Parameter(property = "openapi.lint.reportFile")
    File reportFile;

    /** Format of {@code reportFile}: {@code checkstyle} (default), {@code junit}, {@code json}, {@code codeclimate}, or any of the build log formats. */
    @Parameter(property = "openapi.lint.reportFormat", defaultValue = "checkstyle")
    String reportFormat = "checkstyle";

    /** Fail the build when problems with severity {@code error} are found. */
    @Parameter(property = "openapi.lint.failOnErrors", defaultValue = "true")
    boolean failOnErrors = true;

    /** Fail the build when problems with severity {@code warn} are found. */
    @Parameter(property = "openapi.lint.failOnWarnings", defaultValue = "false")
    boolean failOnWarnings;

    /** Rule ids to skip. */
    @Parameter(property = "openapi.lint.skipRules")
    List<String> skipRules;

    /**
     * Instead of reporting, write all found problems to {@code .redocly.lint-ignore.yaml} next to the
     * configuration file so they are ignored from now on ({@code redocly lint --generate-ignore-file}).
     */
    @Parameter(property = "openapi.lint.generateIgnoreFile", defaultValue = "false")
    boolean generateIgnoreFile;

    @Override
    Goal<?> goal() {
        return new LintGoal(this);
    }
}
