package com.zaneschepke.wireguardautotunnel.domain.model

import com.wgtunnel.backend.model.ProxyConfig
import com.wgtunnel.backend.model.parseProxyBindAddress

data class ProxySettings(
    val id: Long = 0,
    val socks5ProxyEnabled: Boolean = true,
    val socks5ProxyBindAddress: String? = null,
    val httpProxyEnabled: Boolean = true,
    val httpProxyBindAddress: String? = null,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,
    val allowSocks4: Boolean = false,
) {
    // SOCKS4 has no auth mechanism, so the toggle only ever takes effect when no
    // username/password is configured.
    val canAllowSocks4: Boolean
        get() = proxyUsername.isNullOrBlank()

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
                        allowSocks4 = allowSocks4 && canAllowSocks4,
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

    private fun parseAddress(address: String): Pair<String, Int>? = address.parseProxyBindAddress()

    companion object {
        const val DEFAULT_SOCKS_BIND_ADDRESS = "127.0.0.1:25344"
        const val DEFAULT_HTTP_BIND_ADDRESS = "127.0.0.1:25345"
    }
}
