package net.mwalser.openapi.toolkit.mojo

import net.mwalser.openapi.toolkit.redocly.JsLog
import org.apache.maven.plugin.logging.Log

/** Routes output of the JavaScript side to the Maven log, one log entry per line. */
class MavenJsLog(private val log: Log) : JsLog {

    enum class Level(internal val emit: Log.(CharSequence) -> Unit) {
        DEBUG(Log::debug),
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

        /**
         * Logs formatted problems. A problem line carries its own severity (`13:5  error  rule-id ...`); file
         * headers, hints and other formats get the level of the block.
         */
        fun problems(log: Log, text: String, level: Level) = lines(text) { line ->
            val own = when (PROBLEM_LINE.find(line)?.groupValues?.get(1)) {
                "error" -> Level.ERROR
                "warning" -> Level.WARN
                else -> level
            }
            own.emit(log, line)
        }

        private val PROBLEM_LINE = Regex("""^\s*\d+:\d+\s+(error|warning)\b""")

        private fun lines(text: String, emit: (String) -> Unit) =
            text.trimEnd().lineSequence().map { it.trimEnd() }.forEach(emit)
    }
}
