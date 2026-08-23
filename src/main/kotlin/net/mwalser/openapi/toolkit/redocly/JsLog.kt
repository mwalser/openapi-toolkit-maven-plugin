package net.mwalser.openapi.toolkit.redocly

/** Receives log output produced by the JavaScript side. Messages may contain newlines. */
interface JsLog {
    /** Regular command output (what the CLI would print to stdout). */
    fun output(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
    fun debug(message: String)

    companion object {
        /** Discards everything. */
        val SILENT: JsLog = object : JsLog {
            override fun output(message: String) {}
            override fun info(message: String) {}
            override fun warn(message: String) {}
            override fun error(message: String) {}
            override fun debug(message: String) {}
        }
    }
}
