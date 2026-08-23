package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.JsLog
import org.apache.maven.plugin.logging.Log

/** Routes output of the JavaScript side to the Maven log, one log entry per line. */
class MavenJsLog(private val log: Log) : JsLog {

    /** Maven log levels that Redocly results are printed at. */
    enum class Level(internal val emit: Log.(CharSequence) -> Unit) {
        INFO(Log::info),
        WARN(Log::warn),
        ERROR(Log::error),
    }

    override fun output(message: String) = lines(message, log::info)

    override fun info(message: String) = lines(message, log::info)

    override fun warn(message: String) = lines(message, log::warn)

    override fun error(message: String) = lines(message, log::error)

    override fun debug(message: String) {
        if (log.isDebugEnabled) lines(message, log::debug)
    }

    companion object {
        /** Logs a multi-line text at one level. */
        fun block(log: Log, text: String, level: Level) = lines(text) { level.emit(log, it) }

        /** Trailing whitespace and the final newline go; empty messages produce no entry. */
        private fun lines(text: String, emit: (String) -> Unit) =
            text.trimEnd().lineSequence().map { it.trimEnd() }.forEach(emit)
    }
}
