package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.util.DnsError

data class SplitDnsUiState(
    val isLoading: Boolean = true,
    val tunnel: TunnelConfig? = null,
    val domains: List<String> = emptyList(),
    val inputError: DnsError? = null,
    /** True when the tunnel has no DNS server configured, so split DNS has no effect. */
    val tunnelHasDnsServer: Boolean = true,
)
