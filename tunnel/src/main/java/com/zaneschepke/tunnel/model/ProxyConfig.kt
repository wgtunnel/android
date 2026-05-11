package com.zaneschepke.tunnel.model

data class ProxyConfig(val socks5: Socks5? = null, val http: Http? = null) {

    fun toQuickString(): String = buildString {
        socks5?.let {
            appendLine("[Socks5]")
            appendLine("BindAddress = ${it.host}:${it.port}")
            it.username?.let { u -> appendLine("Username = $u") }
            it.password?.let { p -> appendLine("Password = $p") }
        }

        if (socks5 != null && http != null) {
            appendLine()
        }

        http?.let {
            appendLine("[http]")
            appendLine("BindAddress = ${it.host}:${it.port}")
            it.username?.let { u -> appendLine("Username = $u") }
            it.password?.let { p -> appendLine("Password = $p") }
        }
    }

    data class Socks5(
        val host: String = "127.0.0.1",
        val port: Int,
        val username: String? = null,
        val password: String? = null,
    )

    data class Http(
        val host: String = "127.0.0.1",
        val port: Int,
        val username: String? = null,
        val password: String? = null,
    )
}
