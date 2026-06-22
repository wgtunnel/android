package com.zaneschepke.tunnel.model

sealed class DnsBoostrapMode {

    data object System : DnsBoostrapMode()

    data class Custom(val config: DnsBoostrapConfig) : DnsBoostrapMode()
}

sealed class DnsBoostrapConfig(open val upstream: String?) {
    abstract val protocol: String

    data class Plain(override val upstream: String?) : DnsBoostrapConfig(upstream) {
        override val protocol: String
            get() = "plain"
    }

    data class DoH(override val upstream: String?) : DnsBoostrapConfig(upstream) {
        override val protocol: String
            get() = "doh"
    }

    data class DoT(override val upstream: String?) : DnsBoostrapConfig(upstream) {
        override val protocol: String
            get() = "dot"
    }

    companion object {
        const val DEFAULT_UNDERLYING_SERVERS = "1.1.1.1,8.8.8.8"
        const val DEFAULT_PLAIN_UPSTREAM = "1.1.1.1"
        const val DEFAULT_DOH_UPSTREAM = "https://cloudflare-dns.com/dns-query"
        const val DEFAULT_DOT_UPSTREAM = "one.one.one.one"
        val SPECIAL_ANDROID_DOH_SERVERS =
            mapOf(
                "cloudflare-dns.com" to "https://cloudflare-dns.com/dns-query",
                "dns.google" to "https://dns.google/dns-query",
            )
    }
}

data class DnsBootstrapResult(
    val ipv4: List<String> = emptyList(),
    val ipv6: List<String> = emptyList(),
)
