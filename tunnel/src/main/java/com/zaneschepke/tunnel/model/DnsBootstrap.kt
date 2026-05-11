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
            get() = "dot"
    }

    data class DoT(override val upstream: String?) : DnsBoostrapConfig(upstream) {
        override val protocol: String
            get() = "dot"
    }

    companion object {
        const val DEFAULT_UPSTREAM = "1.1.1.1"
    }
}

data class DnsBootstrapResult(
    val ipv4: List<String> = emptyList(),
    val ipv6: List<String> = emptyList(),
)
