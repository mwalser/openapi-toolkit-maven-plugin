package net.mwalser.openapi.toolkit.redocly

import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * The object installed as `globalThis.__jvm` in the JavaScript context. All file system and network
 * access of the embedded Redocly code goes through here. Uses polyglot proxies only, so no host
 * reflection access has to be granted to the guest.
 */
internal class HostBridge(
    @Volatile var log: JsLog = JsLog.SILENT,
    @Volatile var workingDirectory: Path = Path.of("").toAbsolutePath(),
    httpClientFactory: () -> HttpClient = { HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build() },
) {
    private val httpClient: HttpClient by lazy(httpClientFactory)

    fun asProxy(): ProxyObject = ProxyObject.fromMap(
        mapOf<String, Any>(
            "cwd" to exec { JsPaths.toJs(workingDirectory) },
            "env" to exec { args -> System.getenv(args[0].asString()) },
            "log" to exec { args -> dispatchLog(args[0].asString(), args[1].asString()); null },
            "nowMillis" to exec { System.currentTimeMillis().toDouble() },
            "parseUrl" to exec { args -> parseUrl(args[0].asString(), args[1].takeUnless { it.isNull }?.asString()) },
            "utf8Encode" to exec { args ->
                ProxyArray.fromList(args[0].asString().toByteArray(StandardCharsets.UTF_8).map { (it.toInt() and 0xff) as Any })
            },
            "utf8Decode" to exec { args -> utf8Decode(args[0]) },
            "fetch" to exec { args -> fetch(args[0].asString(), args[1].asString(), args[2].asString(), args[3].takeUnless { it.isNull }?.asString()) },

            // file system
            "readFile" to exec { args -> path(args[0]).let { if (Files.isRegularFile(it)) Files.readString(it) else null } },
            "exists" to exec { args -> Files.exists(path(args[0])) },
            "statKind" to exec { args ->
                val p = path(args[0])
                when {
                    Files.isDirectory(p) -> "dir"
                    Files.exists(p) -> "file"
                    else -> null
                }
            },
            "writeFile" to exec { args ->
                val p = path(args[0])
                p.parent?.let { Files.createDirectories(it) }
                Files.writeString(p, args[1].asString())
                null
            },
            "mkdirs" to exec { args -> Files.createDirectories(path(args[0])); null },
            "readdir" to exec { args ->
                val p = path(args[0])
                if (!Files.isDirectory(p)) null
                else ProxyArray.fromList(Files.list(p).use { s -> s.map { it.fileName.toString() as Any }.toList() })
            },
            "delete" to exec { args -> Files.deleteIfExists(path(args[0])); null },
        ),
    )

    private fun exec(fn: (Array<Value>) -> Any?) = ProxyExecutable { args -> fn(args) }

    private fun path(v: Value): Path = JsPaths.toHostPath(v.asString())

    private fun dispatchLog(level: String, message: String) {
        when (level) {
            "output" -> log.output(message)
            "info" -> log.info(message)
            "warn" -> log.warn(message)
            "error" -> log.error(message)
            else -> log.debug(message)
        }
    }

    private fun utf8Decode(v: Value): String {
        val bytes = ByteArray(v.arraySize.toInt()) { v.getArrayElement(it.toLong()).asInt().toByte() }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun fetch(url: String, method: String, headersJson: String, body: String?): ProxyObject {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(60))
            .method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body))
        Json.mapper.readTree(headersJson).properties().forEach { (name, value) -> request.header(name, value.asText()) }
        val response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString())
        val headers = response.headers().map().mapValues { (_, v) -> v.joinToString(", ") }
        return ProxyObject.fromMap(
            mapOf(
                "status" to response.statusCode(),
                "statusText" to "",
                "body" to response.body(),
                "headersJson" to Json.mapper.writeValueAsString(headers),
            ),
        )
    }

    /** Backs the WHATWG `URL` polyfill with `java.net.URI`. Returns null for unparsable input. */
    private fun parseUrl(input: String, base: String?): ProxyObject? {
        val uri = try {
            val parsed = if (base != null) URI(base).resolve(input) else URI(input)
            if (parsed.scheme == null) return null
            parsed.normalize()
        } catch (e: Exception) {
            return null
        }
        val hostname = uri.host ?: ""
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
                "pathname" to (uri.rawPath?.ifEmpty { "/" } ?: "/"),
                "search" to (uri.rawQuery?.let { "?$it" } ?: ""),
                "hash" to (uri.rawFragment?.let { "#$it" } ?: ""),
                "origin" to if (hostname.isEmpty()) "null" else "$protocol//$host",
            ),
        )
    }
}
