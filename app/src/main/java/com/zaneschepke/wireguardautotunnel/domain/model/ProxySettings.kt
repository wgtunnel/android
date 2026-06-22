package com.zaneschepke.wireguardautotunnel.domain.model

import com.zaneschepke.tunnel.model.ProxyConfig

data class ProxySettings(
    val id: Long = 0,
    val socks5ProxyEnabled: Boolean = false,
    val socks5ProxyBindAddress: String? = null,
    val httpProxyEnabled: Boolean = false,
    val httpProxyBindAddress: String? = null,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,
) {

    fun toProxyConfig(): ProxyConfig {
        val socks5 =
            if (socks5ProxyEnabled) {
                parseAddress(socks5ProxyBindAddress ?: DEFAULT_SOCKS_BIND_ADDRESS)?.let {
                    (host, port) ->
                    ProxyConfig.Socks5(
                        host = host,
                        port = port,
                        username = proxyUsername,
                        password = proxyPassword,
                    )
                }
            } else null

        val http =
            if (httpProxyEnabled) {
                parseAddress(httpProxyBindAddress ?: DEFAULT_HTTP_BIND_ADDRESS)?.let { (host, port)
                    ->
                    ProxyConfig.Http(
                        host = host,
                        port = port,
                        username = proxyUsername,
                        password = proxyPassword,
                    )
                }
            } else null

        return ProxyConfig(socks5 = socks5, http = http)
    }

    private fun parseAddress(address: String): Pair<String, Int>? {
        val parts = address.split(":")
        if (parts.size != 2) return null

        val host = parts[0]
        val port = parts[1].toIntOrNull() ?: return null

        return host to port
    }

    companion object {
        const val DEFAULT_SOCKS_BIND_ADDRESS = "127.0.0.1:25344"
        const val DEFAULT_HTTP_BIND_ADDRESS = "127.0.0.1:25345"
    }
}
