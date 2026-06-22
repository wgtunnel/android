package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.networkmonitor.DnsInfo
import com.zaneschepke.wireguardautotunnel.domain.model.DnsSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.util.DnsError

data class DnsUiState(
    val dnsSettings: DnsSettings = DnsSettings(),
    val isLoading: Boolean = true,
    val globalTunnelConfig: TunnelConfig? = null,
    val peerResolutionEndpointError: DnsError? = null,
    val systemDnsInfo: DnsInfo? = null,
)
