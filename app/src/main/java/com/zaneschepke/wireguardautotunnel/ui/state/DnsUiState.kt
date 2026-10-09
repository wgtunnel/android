package com.zaneschepke.wireguardautotunnel.ui.state

import com.wgtunnel.backend.model.dns.DnsValidationError
import com.zaneschepke.networkmonitor.DnsInfo
import com.zaneschepke.wireguardautotunnel.domain.model.DnsSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

data class DnsUiState(
    val dnsSettings: DnsSettings = DnsSettings(),
    val isLoading: Boolean = true,
    val globalTunnelConfig: TunnelConfig? = null,
    val bootstrapEndpointError: DnsValidationError? = null,
    val tunnelEndpointError: DnsValidationError? = null,
    val localSuffixesError: DnsValidationError? = null,
    val systemDnsInfo: DnsInfo? = null,
    val hasActiveTunnel: Boolean = false,
)
