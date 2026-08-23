package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.JsLog
import org.apache.maven.plugin.logging.Log

/** Routes JavaScript log output to the Maven log, one Maven log line per text line. */
class MavenJsLog(private val log: Log) : JsLog {
    override fun output(message: String) = lines(message) { log.info(it) }
    override fun info(message: String) = lines(message) { log.info(it) }
    override fun warn(message: String) = lines(message) { log.warn(it) }
    override fun error(message: String) = lines(message) { log.error(it) }
    override fun debug(message: String) = lines(message) { if (log.isDebugEnabled) log.debug(it) }

    private inline fun lines(message: String, emit: (String) -> Unit) {
        val text = message.trimEnd('\n', '\r')
        if (text.isEmpty()) return
        text.lineSequence().forEach { emit(it.trimEnd()) }
    }

    companion object {
        /** Logs a multi-line block produced by Redocly (e.g. formatted problems) at the given level. */
        fun block(log: Log, text: String, level: Level) {
            val trimmed = text.trimEnd('\n', '\r')
            if (trimmed.isEmpty()) return
            for (line in trimmed.lineSequence()) {
                val l = line.trimEnd()
                when (level) {
                    Level.INFO -> log.info(l)
                    Level.WARN -> log.warn(l)
                    Level.ERROR -> log.error(l)
                }
            }
        }
    }

    enum class Level { INFO, WARN, ERROR }
}
