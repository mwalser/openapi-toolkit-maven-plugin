package net.mwalser.openapi.toolkit.redocly

/** A failure reported by the embedded Redocly code or by the engine hosting it. */
class RedoclyException(
    message: String,
    /** The JavaScript error name (e.g. `CommandError`, `ResolveError`), if known. */
    val jsName: String? = null,
    /** The JavaScript stack trace, if available. */
    val jsStack: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
