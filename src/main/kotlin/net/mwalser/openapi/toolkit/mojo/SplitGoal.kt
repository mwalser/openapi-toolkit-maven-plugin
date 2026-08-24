package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.JsPaths
import net.mwalser.openapi.toolkit.redocly.SplitOptions
import org.apache.maven.plugin.MojoExecutionException

internal class SplitGoal(mojo: SplitMojo) : Goal<SplitMojo>(mojo) {

    override val skipGoal get() = SkipParameter("openapi.split.skip", mojo.skipSplit)

    override fun validate() {
        super.validate()
        if (mojo.separator.isNullOrBlank()) throw MojoExecutionException("Invalid value for openapi.split.separator; must not be blank")
    }

    override fun run() {
        val result = redocly().split(
            SplitOptions(cwd = jsCwd, api = JsPaths.toJs(mojo.api), outDir = jsPath(resolve(mojo.outputDirectory)), separator = mojo.separator),
        )
        MavenJsLog.block(log, result.output, MavenJsLog.Level.INFO)
        log.info("Split ${display(result.api)} into ${display(result.outDir)}")
    }
}
