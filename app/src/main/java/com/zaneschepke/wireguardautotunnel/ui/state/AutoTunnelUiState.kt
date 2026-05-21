package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.networkmonitor.ConnectivityState
import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

data class AutoTunnelUiState(
    val connectivityState: ConnectivityState? = null,
    val autoTunnelActive: Boolean = false,
    val autoTunnelSettings: AutoTunnelSettings = AutoTunnelSettings(),
    val isBatteryOptimizationShown: Boolean = false,
    val isLocationDisclosureShown: Boolean = false,
    val tunnels: List<TunnelConfig> = emptyList(),
    val isLoading: Boolean = true,
)
