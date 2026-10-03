package net.mwalser.openapi.toolkit.mojo;

import java.io.File;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Lints API descriptions ({@code redocly lint}). Fails the build on problems with severity {@code error}; see
 * {@code failOnErrors} and {@code failOnWarnings}.
 */
@Mojo(name = "lint", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class LintMojo extends AbstractApiMojo {

    /** Replaces the {@code extends} list of the configuration. Built-in rulesets: {@code minimal}, {@code recommended}, {@code recommended-strict}, {@code spec}. */
    @Parameter(property = "openapi.toolkit.extends", alias = "extends")
    List<String> extendsRulesets;

    /**
     * Format of the problems in the build log: {@code stylish}, {@code codeframe}, {@code summary}, {@code markdown}
     * or {@code github-actions} (workflow annotations, printed without the Maven prefix). For machine-readable
     * formats use {@code reportFile}.
     */
    @Parameter(property = "openapi.toolkit.lint.format", defaultValue = "stylish")
    String format = "stylish";

    /** File to write all problems to, in {@code reportFormat}; not limited by {@code maxProblems}. The build log is still written. */
    @Parameter(property = "openapi.toolkit.lint.reportFile")
    File reportFile;

    /** Format of {@code reportFile}: {@code checkstyle}, {@code junit}, {@code json}, {@code codeclimate}, or any {@code format} value. */
    @Parameter(property = "openapi.toolkit.lint.reportFormat", defaultValue = "checkstyle")
    String reportFormat = "checkstyle";

    /** Fail the build when problems with severity {@code error} are found. */
    @Parameter(property = "openapi.toolkit.lint.failOnErrors", defaultValue = "true")
    boolean failOnErrors = true;

    /** Fail the build when problems with severity {@code warn} are found. */
    @Parameter(property = "openapi.toolkit.lint.failOnWarnings", defaultValue = "false")
    boolean failOnWarnings;

    /** Rules not to evaluate, by rule id (for example {@code info-license}). */
    @Parameter(property = "openapi.toolkit.lint.skipRules")
    List<String> skipRules;

    /**
     * Write every problem found to {@code .redocly.lint-ignore.yaml} next to the configuration file (or in the
     * project base directory without one) instead of reporting it; later runs ignore those problems
     * ({@code redocly lint --generate-ignore-file}). Does not fail the build.
     */
    @Parameter(property = "openapi.toolkit.lint.generateIgnoreFile", defaultValue = "false")
    boolean generateIgnoreFile;

    /** Skip this goal only; {@code openapi.toolkit.skip} skips every goal of the plugin. */
    @Parameter(property = "openapi.toolkit.lint.skip", defaultValue = "false")
    boolean skipLint;

    @Override
    Goal<?> goal() {
        return new LintGoal(this);
    }
}
