package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

data class TunnelsUiState(
    val tunnels: List<TunnelConfig> = emptyList(),
    val backendStatus: BackendStatus = BackendStatus(),
    val selectedTunnels: List<TunnelConfig> = emptyList(),
    val isLoading: Boolean = true,
)
