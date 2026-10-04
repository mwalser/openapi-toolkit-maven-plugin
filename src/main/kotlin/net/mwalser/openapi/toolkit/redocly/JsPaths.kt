package net.mwalser.openapi.toolkit.redocly

import java.io.File
import java.nio.file.Path

/**
 * Converts between host file system paths and the paths handed to the JavaScript side.
 *
 * The bundled `path` implementation is POSIX-only, so Windows paths are presented as `/C:/dir/file`
 * (absolute in POSIX terms) and mapped back to `C:\dir\file` at the host bridge. On other platforms
 * paths pass through unchanged.
 */
object JsPaths {
    private val windowsDrive = Regex("^[A-Za-z]:[\\\\/]")
    private val jsWindowsDrive = Regex("^/[A-Za-z]:/")
    private val urlScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    @Volatile
    internal var windows: Boolean = System.getProperty("os.name", "").lowercase().contains("win")

    /** Host path → JS path. Relative paths only get their separators normalized. */
    @JvmStatic
    fun toJs(hostPath: String): String {
        if (!windows) return hostPath
        val slashed = hostPath.replace('\\', '/')
        return if (windowsDrive.containsMatchIn(hostPath)) "/$slashed" else slashed
    }

    @JvmStatic
    fun toJs(path: Path): String = toJs(path.toString())

    @JvmStatic
    fun toJs(file: File): String = toJs(file.path)

    /** Whether the string is a URL (`scheme://...`) rather than a path. */
    @JvmStatic
    fun isUrl(path: String): Boolean = urlScheme.containsMatchIn(path)

    /** JS path → host path. URLs pass through unchanged. */
    @JvmStatic
    fun toHost(jsPath: String): String {
        if (!windows || isUrl(jsPath)) return jsPath
        val withoutLeadingSlash = if (jsWindowsDrive.containsMatchIn(jsPath)) jsPath.drop(1) else jsPath
        return withoutLeadingSlash.replace('/', '\\')
    }

    @JvmStatic
    fun toHostPath(jsPath: String): Path = Path.of(toHost(jsPath))
}
