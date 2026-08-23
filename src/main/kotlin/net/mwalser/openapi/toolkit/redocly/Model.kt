package net.mwalser.openapi.toolkit.redocly

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// ---- shared -------------------------------------------------------------------------------------

data class Totals(val errors: Int = 0, val warnings: Int = 0, val ignored: Int = 0)

data class ProblemLocation(val file: String? = null, val pointer: String? = null, val line: Int? = null, val col: Int? = null)

data class Problem(
    val ruleId: String,
    val severity: String,
    val message: String,
    val ignored: Boolean = false,
    val suggest: List<String> = emptyList(),
    val location: List<ProblemLocation> = emptyList(),
)

/** Result of linting `redocly.yaml` itself. */
data class ConfigLintResult(val totals: Totals, val problems: List<Problem> = emptyList(), val output: String = "")

/** Rules/preprocessors/decorators configured in `redocly.yaml` that no plugin provides. */
data class UnusedConfig(
    val rules: List<String> = emptyList(),
    val preprocessors: List<String> = emptyList(),
    val decorators: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = rules.isEmpty() && preprocessors.isEmpty() && decorators.isEmpty()
}

// ---- lint ---------------------------------------------------------------------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
data class LintOptions(
    val cwd: String,
    val configPath: String? = null,
    val apis: List<String> = emptyList(),
    val extends: List<String>? = null,
    val format: String = "stylish",
    val reportFormat: String? = null,
    val maxProblems: Int = 100,
    val skipRules: List<String>? = null,
    val skipPreprocessors: List<String>? = null,
    val generateIgnoreFile: Boolean = false,
    val lintConfig: String = "warn",
)

data class ApiLintResult(
    val path: String,
    val alias: String? = null,
    val totals: Totals,
    val durationMillis: Long = 0,
    val problems: List<Problem> = emptyList(),
    /** Problems formatted in the requested console format. */
    val output: String = "",
)

data class IgnoreFileResult(val ignored: Int)

data class LintResult(
    val usedDefaultConfig: Boolean = false,
    val configLint: ConfigLintResult? = null,
    val apis: List<ApiLintResult> = emptyList(),
    val totals: Totals,
    /** All problems formatted in `reportFormat`, when one was requested. */
    val report: String? = null,
    val ignoreFile: IgnoreFileResult? = null,
    val unused: UnusedConfig = UnusedConfig(),
)

// ---- bundle -------------------------------------------------------------------------------------

data class BundleOptions(
    val cwd: String,
    val configPath: String? = null,
    val apis: List<String> = emptyList(),
    val extends: List<String>? = null,
    val outputDirectory: String,
    val outputFile: String? = null,
    val ext: String = "yaml",
    val dereferenced: Boolean = false,
    val force: Boolean = false,
    val removeUnusedComponents: Boolean = false,
    val keepUrlReferences: Boolean = false,
    val componentNamesStrategy: String? = null,
    val componentRenamingConflicts: String? = null,
    val skipDecorators: List<String>? = null,
    val skipPreprocessors: List<String>? = null,
    val lintConfig: String = "warn",
    val format: String = "codeframe",
    val maxProblems: Int = 100,
)

data class ApiBundleResult(
    val path: String,
    val alias: String? = null,
    val outputFile: String,
    val written: Boolean,
    val totals: Totals,
    val durationMillis: Long = 0,
    val removedComponents: Int = 0,
    val problems: List<Problem> = emptyList(),
    val output: String = "",
)

data class BundleResult(
    val configLint: ConfigLintResult? = null,
    val apis: List<ApiBundleResult> = emptyList(),
    val totals: Totals,
    val unused: UnusedConfig = UnusedConfig(),
)

// ---- check-config -------------------------------------------------------------------------------

data class CheckConfigOptions(
    val cwd: String,
    val configPath: String,
    val severity: String = "warn",
    val format: String = "stylish",
    val maxProblems: Int = 100,
)

data class CheckConfigResult(val configLint: ConfigLintResult? = null)

// ---- stats --------------------------------------------------------------------------------------

data class StatsOptions(
    val cwd: String,
    val configPath: String? = null,
    val api: String? = null,
    val format: String = "stylish",
    val lintConfig: String = "warn",
)

data class StatsResult(
    val configLint: ConfigLintResult? = null,
    val path: String,
    val alias: String? = null,
    val format: String,
    /** The statistics formatted as requested. */
    val output: String = "",
    /** The statistics as structured data; only available for `format = json`. */
    val stats: Map<String, Any?>? = null,
)

// ---- join ---------------------------------------------------------------------------------------

data class JoinOptions(
    val cwd: String,
    val configPath: String? = null,
    val apis: List<String>,
    val output: String,
    val prefixTagsWithInfoProp: String? = null,
    val prefixTagsWithFilename: Boolean = false,
    val prefixComponentsWithInfoProp: String? = null,
    val withoutXTagGroups: Boolean = false,
    val lintConfig: String = "warn",
)

data class JoinResult(val configLint: ConfigLintResult? = null, val outputFile: String, val output: String = "")

// ---- split --------------------------------------------------------------------------------------

data class SplitOptions(val cwd: String, val api: String, val outDir: String, val separator: String = "_")

data class SplitResult(val api: String, val outDir: String, val output: String = "")

// ---- score --------------------------------------------------------------------------------------

data class ScoreOptions(
    val cwd: String,
    val configPath: String? = null,
    val api: String? = null,
    val format: String = "stylish",
    val operationDetails: Boolean = false,
    val lintConfig: String = "warn",
)

data class ScoreResult(
    val configLint: ConfigLintResult? = null,
    val path: String,
    val alias: String? = null,
    val format: String,
    val output: String = "",
    /** The score as structured data; only available for `format = json`. */
    val score: Map<String, Any?>? = null,
)
