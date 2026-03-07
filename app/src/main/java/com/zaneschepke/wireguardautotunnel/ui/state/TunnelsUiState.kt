package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelState

data class TunnelsUiState(
    val tunnels: List<TunnelConfig> = emptyList(),
    val activeTunnels: Map<Int, TunnelState> = emptyMap(),
    val selectedTunnels: List<TunnelConfig> = emptyList(),
    val isPingEnabled: Boolean = false,
    val showPingStats: Boolean = false,
    val isLoading: Boolean = true,
)
