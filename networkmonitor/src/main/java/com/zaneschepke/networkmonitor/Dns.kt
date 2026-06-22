package com.zaneschepke.networkmonitor

enum class PrivateDnsMode {
    OFF,
    AUTOMATIC,
    HOSTNAME,
}

data class DnsInfo(
    val servers: List<String> = emptyList(),
    val privateDnsMode: PrivateDnsMode = PrivateDnsMode.OFF,
    val privateDnsHostname: String? = null,
) {
    val isEmpty: Boolean
        get() = servers.isEmpty()

    override fun toString() =
        "DnsInfo(servers=$servers, mode=$privateDnsMode, hostname=$privateDnsHostname)"
}
