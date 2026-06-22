package com.zaneschepke.wireguardautotunnel.domain.model

import com.zaneschepke.wireguardautotunnel.domain.enums.DnsProtocol

data class DnsSettings(
    val id: Int = 0,
    val dnsProtocol: DnsProtocol = DnsProtocol.fromValue(0),
    val dnsEndpoint: String? = null,
    val isGlobalTunnelDnsEnabled: Boolean = false,
)
