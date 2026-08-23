package net.mwalser.openapi.toolkit.redocly

import java.nio.file.Path

/**
 * Typed facade over the commands of the embedded Redocly bundle.
 *
 * All paths in the option objects must be JS paths (see [JsPaths.toJs]); paths in results are JS paths
 * as well (see [JsPaths.toHost]). On non-Windows platforms both are plain host paths.
 */
class Redocly(private val runtime: RedoclyRuntime, private val log: JsLog = JsLog.SILENT) {

    val version: String get() = runtime.redoclyVersion
    val effectiveEngine: RedoclyRuntime.EffectiveEngine get() = runtime.effectiveEngine

    fun lint(options: LintOptions): LintResult =
        runtime.run("lint", options, JsPaths.toHostPath(options.cwd), log)

    fun bundle(options: BundleOptions): BundleResult =
        runtime.run("bundle", options, JsPaths.toHostPath(options.cwd), log)

    fun checkConfig(options: CheckConfigOptions): CheckConfigResult =
        runtime.run("check-config", options, JsPaths.toHostPath(options.cwd), log)

    fun stats(options: StatsOptions): StatsResult =
        runtime.run("stats", options, JsPaths.toHostPath(options.cwd), log)

    fun join(options: JoinOptions): JoinResult =
        runtime.run("join", options, JsPaths.toHostPath(options.cwd), log)

    fun score(options: ScoreOptions): ScoreResult =
        runtime.run("score", options, JsPaths.toHostPath(options.cwd), log)

    fun split(options: SplitOptions): SplitResult =
        runtime.run("split", options, JsPaths.toHostPath(options.cwd), log)
}
