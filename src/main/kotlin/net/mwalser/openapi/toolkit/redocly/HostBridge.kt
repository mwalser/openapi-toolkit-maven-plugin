package net.mwalser.openapi.toolkit.redocly

import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.HttpTimeoutException
import java.nio.channels.UnresolvedAddressException
import java.nio.charset.StandardCharsets
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The object installed as `globalThis.__jvm` in the JavaScript context. All file system and network access of
 * the embedded Redocly code goes through here. Uses polyglot proxies only, so the guest needs no host access.
 *
 * Failures reach the guest as [HostCallException]s; `js/src/host.js` turns them into Node-style errors.
 * Everything here runs on the runtime's JavaScript thread.
 */
internal class HostBridge {
    var log: JsLog = JsLog.SILENT
    var workingDirectory: Path = Path.of("").toAbsolutePath()
    var network: NetworkConfig = NetworkConfig()
        set(value) {
            if (value != field) httpClient = null
            field = value
        }

    private var httpClient: HttpClient? = null

    fun asProxy(): ProxyObject = ProxyObject.fromMap(
        mapOf<String, Any>(
            "cwd" to function { JsPaths.toJs(workingDirectory) },
            "env" to function { args -> System.getenv(args[0].asString()) },
            "log" to action { args -> log(args[0].asString(), args[1].asString()) },
            "parseUrl" to function { args -> parseUrl(args[0].asString(), args[1].asStringOrNull()) },
            "fetch" to function { args -> fetch(args[0].asString(), args[1].asString(), args[2], args[3].asStringOrNull()) },
            "exists" to function { args -> Files.exists(path(args[0])) },
            "statKind" to function { args -> statKind(path(args[0])) },
            "readFile" to function { args -> readFile(path(args[0])) },
            "readdir" to function { args -> readdir(path(args[0])) },
            "writeFile" to action { args -> writeFile(path(args[0]), args[1].asString()) },
            "mkdirs" to action { args -> Files.createDirectories(path(args[0])) },
        ),
    )

    private fun function(body: (Array<Value>) -> Any?) = ProxyExecutable { args -> guarded { body(args) } }

    private fun action(body: (Array<Value>) -> Unit) = ProxyExecutable { args -> guarded { body(args) }; null }

    private inline fun <T> guarded(body: () -> T): T = try {
        body()
    } catch (e: HostCallException) {
        throw e
    } catch (e: Exception) {
        throw HostCallException.of(e)
    }

    private fun path(value: Value): Path = workingDirectory.resolve(JsPaths.toHostPath(value.asString())).normalize()

    private fun log(level: String, message: String) = when (level) {
        "output" -> log.output(message)
        "info" -> log.info(message)
        "warn" -> log.warn(message)
        "error" -> log.error(message)
        else -> log.debug(message)
    }

    private fun statKind(path: Path): String? = when {
        Files.isDirectory(path) -> "dir"
        Files.exists(path) -> "file"
        else -> null
    }

    /** Decodes leniently like Node does, so a stray Latin-1 byte yields a replacement character, not a failure. */
    private fun readFile(path: Path): String? =
        if (Files.isRegularFile(path)) String(Files.readAllBytes(path), StandardCharsets.UTF_8) else null

    private fun readdir(path: Path): ProxyArray? =
        if (Files.isDirectory(path)) ProxyArray.fromList(path.listDirectoryEntries().map { it.name }) else null

    private fun writeFile(path: Path, content: String) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, content)
    }

    private fun fetch(url: String, method: String, headers: Value, body: String?): ProxyObject {
        if (network.offline) throw HostCallException("ENETDOWN", "Maven is offline (-o), remote references are not fetched")
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(REQUEST_TIMEOUT)
            .method(method, if (body == null) BodyPublishers.noBody() else BodyPublishers.ofString(body))
        for (name in headers.memberKeys) request.header(name, headers.getMember(name).asString())
        val response = httpClient().send(request.build(), BodyHandlers.ofString())
        val responseHeaders = response.headers().map().mapValues { (_, values) -> values.joinToString(", ") }
        return ProxyObject.fromMap(
            mapOf(
                "status" to response.statusCode(),
                "body" to response.body(),
                "headers" to ProxyObject.fromMap(responseHeaders),
            ),
        )
    }

    private fun httpClient(): HttpClient = httpClient ?: network.newHttpClient().also { httpClient = it }

    /** Backs the WHATWG `URL` polyfill with `java.net.URI`. Returns null for unparsable input. */
    private fun parseUrl(input: String, base: String?): ProxyObject? {
        val uri = try {
            (if (base == null) URI(input) else URI(base).resolve(input)).normalize()
        } catch (e: Exception) {
            return null
        }
        if (uri.scheme == null) return null
        val hostname = uri.host.orEmpty()
        val port = if (uri.port == -1) "" else uri.port.toString()
        val host = if (port.isEmpty()) hostname else "$hostname:$port"
        val protocol = "${uri.scheme}:"
        return ProxyObject.fromMap(
            mapOf(
                "href" to uri.toString(),
                "protocol" to protocol,
                "host" to host,
                "hostname" to hostname,
                "port" to port,
                "pathname" to uri.rawPath.orEmpty().ifEmpty { "/" },
                "search" to uri.rawQuery?.let { "?$it" }.orEmpty(),
                "hash" to uri.rawFragment?.let { "#$it" }.orEmpty(),
                "origin" to if (hostname.isEmpty()) "null" else "$protocol//$host",
            ),
        )
    }

    private companion object {
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}

private fun Value.asStringOrNull(): String? = if (isNull) null else asString()

/**
 * A failed host call as the guest sees it. The message starts with the Node.js error code that the JavaScript
 * side attaches to the error it raises in turn, e.g. `ENOENT: no such file or directory`.
 */
internal class HostCallException(code: String, description: String) : RuntimeException("$code: $description") {

    companion object {
        fun of(failure: Exception): HostCallException {
            // HttpClient reports an unresolvable host as a ConnectException; only a cause names the real problem
            val chain = generateSequence<Throwable>(failure) { it.cause }
            if (chain.any { it is UnknownHostException || it is UnresolvedAddressException }) {
                return HostCallException("ENOTFOUND", "host not found")
            }
            return when (failure) {
                is NoSuchFileException -> HostCallException("ENOENT", "no such file or directory")
                is AccessDeniedException -> HostCallException("EACCES", "permission denied")
                is NotDirectoryException -> HostCallException("ENOTDIR", "not a directory")
                is FileAlreadyExistsException -> HostCallException("EEXIST", "file already exists")
                is ConnectException -> HostCallException("ECONNREFUSED", "connection refused")
                is HttpTimeoutException -> HostCallException("ETIMEDOUT", "request timed out")
                else -> HostCallException("EIO", failure.message ?: failure.toString())
            }
        }
    }
}
