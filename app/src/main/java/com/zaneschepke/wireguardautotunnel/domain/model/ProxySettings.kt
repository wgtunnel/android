package com.zaneschepke.wireguardautotunnel.domain.model

data class ProxySettings(
    val id: Long = 0,
    val socks5ProxyEnabled: Boolean = false,
    val socks5ProxyBindAddress: String? = null,
    val httpProxyEnabled: Boolean = false,
    val httpProxyBindAddress: String? = null,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,

    // === НОВЫЕ НАСТРОЙКИ ===
    // Кастомные порты
    val socks5Port: Int = DEFAULT_SOCKS_PORT,
    val httpProxyPort: Int = DEFAULT_HTTP_PORT,

    // LAN раздача
    val enableLanSharing: Boolean = false,
    val lanBindAddress: String = "0.0.0.0",  // Для доступа из локальной сети
    val lanSocks5Port: Int = 1080,
    val lanHttpProxyPort: Int = 3128,

    // Авторизация
    val requireProxyAuthentication: Boolean = false,
    val authenticationMethod: String = "BASIC",  // BASIC, DIGEST

    // Контроль доступа
    val allowedIpRanges: List<String> = listOf(
        "192.168.0.0/16",
        "10.0.0.0/8",
        "172.16.0.0/12"
    ),
    val maxConnections: Int = 100,
    val connectionTimeoutSeconds: Int = 30
) {
    companion object {
        const val DEFAULT_SOCKS_BIND_ADDRESS = "127.0.0.1:25344"
        const val DEFAULT_HTTP_BIND_ADDRESS = "127.0.0.1:25345"
        const val DEFAULT_SOCKS_PORT = 1080
        const val DEFAULT_HTTP_PORT = 3128
    }

    fun getSocks5BindAddress(): String {
        return if (socks5ProxyBindAddress != null) {
            if (socks5Port != DEFAULT_SOCKS_PORT) {
                val host = socks5ProxyBindAddress.split(":")[0]
                "$host:$socks5Port"
            } else {
                socks5ProxyBindAddress
            }
        } else {
            "127.0.0.1:$socks5Port"
        }
    }

    fun getHttpProxyBindAddress(): String {
        return if (httpProxyBindAddress != null) {
            if (httpProxyPort != DEFAULT_HTTP_PORT) {
                val host = httpProxyBindAddress.split(":")[0]
                "$host:$httpProxyPort"
            } else {
                httpProxyBindAddress
            }
        } else {
            "127.0.0.1:$httpProxyPort"
        }
    }
}
