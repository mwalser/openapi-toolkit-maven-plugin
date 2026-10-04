package net.mwalser.openapi.toolkit.redocly

/**
 * Typed entry points to the Redocly commands of the embedded bundle.
 *
 * All paths in the option objects must be JS paths (see [JsPaths.toJs]); paths in results are JS paths
 * as well (see [JsPaths.toHost]). On non-Windows platforms both are plain host paths.
 */
class Redocly(
    private val runtime: RedoclyRuntime,
    private val log: JsLog = JsLog.SILENT,
    private val network: NetworkConfig = NetworkConfig(),
) {
    val version: String get() = runtime.redoclyVersion

    fun lint(options: LintOptions): LintResult = run("lint", options)

    fun bundle(options: BundleOptions): BundleResult = run("bundle", options)

    fun checkConfig(options: CheckConfigOptions): CheckConfigResult = run("check-config", options)

    fun stats(options: StatsOptions): StatsResult = run("stats", options)

    fun score(options: ScoreOptions): ScoreResult = run("score", options)

    fun join(options: JoinOptions): JoinResult = run("join", options)

    fun split(options: SplitOptions): SplitResult = run("split", options)

    fun buildDocs(options: BuildDocsOptions): BuildDocsResult = run("build-docs", options)

    private inline fun <reified T> run(command: String, options: CommandOptions): T = runtime.run(command, options, log, network)
}
