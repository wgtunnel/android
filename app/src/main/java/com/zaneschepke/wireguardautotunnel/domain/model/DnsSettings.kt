package com.zaneschepke.wireguardautotunnel.domain.model

import com.zaneschepke.wireguardautotunnel.domain.enums.BootstrapDnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsMode
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsProtocol

data class DnsSettings(
    val id: Int = 0,
    val bootstrapDnsProtocol: BootstrapDnsProtocol = BootstrapDnsProtocol.fromValue(0),
    val bootstrapDnsEndpoint: String? = null,
    val isGlobalTunnelConfigDnsEnabled: Boolean = false,
    val tunnelDnsMode: TunnelDnsMode = TunnelDnsMode.Off,
    val tunnelDnsProtocol: TunnelDnsProtocol = TunnelDnsProtocol.Doh,
    val tunnelDnsEndpoint: String? = null,
    val useTunnelDnsServersInSplit: Boolean = true,
    val localSuffixes: String? = null,
)
