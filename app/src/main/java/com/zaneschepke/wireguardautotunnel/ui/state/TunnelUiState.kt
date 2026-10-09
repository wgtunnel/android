package com.zaneschepke.wireguardautotunnel.ui.state

import com.wgtunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

data class TunnelUiState(
    val tunnel: TunnelConfig? = null,
    val userTunnels: List<TunnelConfig> = emptyList(),
    val activeConfig: ActiveConfig? = null,
    val lastStatsAtMs: Long = 0L,
    val includedAppsCount: Int? = null,
    val excludedAppsCount: Int? = null,
    val isLoading: Boolean = true,
)
