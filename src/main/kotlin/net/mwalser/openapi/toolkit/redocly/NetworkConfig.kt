package net.mwalser.openapi.toolkit.redocly

import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

/**
 * How remote `$ref`s and `extends` URLs are fetched. The Maven goals derive it from the build's offline flag
 * and the active proxy of `settings.xml`.
 */
data class NetworkConfig(val offline: Boolean = false, val proxy: ProxyConfig? = null) {

    /** An HTTP client following this configuration. */
    fun newHttpClient(): HttpClient {
        val builder = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
        if (proxy != null) {
            builder.proxy(proxy.selector())
            proxy.authenticator()?.let(builder::authenticator)
        }
        return builder.build()
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)
    }
}

/** An HTTP proxy as configured in `settings.xml`; [nonProxyHosts] uses Maven's `|`-separated wildcard syntax. */
data class ProxyConfig(
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val nonProxyHosts: String? = null,
) {
    private val bypassed: List<Regex> = nonProxyHosts.orEmpty().split('|')
        .filter { it.isNotBlank() }
        .map { pattern -> Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) }, RegexOption.IGNORE_CASE) }

    fun selector(): ProxySelector = object : ProxySelector() {
        private val viaProxy = listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)))
        private val direct = listOf(Proxy.NO_PROXY)

        override fun select(uri: URI): List<Proxy> = if (bypassed.any { it.matches(uri.host.orEmpty()) }) direct else viaProxy

        override fun connectFailed(uri: URI, address: SocketAddress, failure: IOException) = Unit
    }

    /** Answers the proxy's own authentication challenges, or null when no credentials are configured. */
    fun authenticator(): Authenticator? = username?.let { user ->
        object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? =
                if (requestorType == RequestorType.PROXY && requestingHost.equals(host, ignoreCase = true) && requestingPort == port) {
                    PasswordAuthentication(user, password.orEmpty().toCharArray())
                } else {
                    null
                }
        }
    }

    /** Leaves the password out. */
    override fun toString(): String = "ProxyConfig(host=$host, port=$port, username=$username, nonProxyHosts=$nonProxyHosts)"
}
