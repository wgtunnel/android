package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.MonitoringSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

data class SettingUiState(
    val settings: GeneralSettings = GeneralSettings(),
    val isLocalLoggingEnabled: Boolean = false,
    val remoteKey: String? = null,
    val isRemoteEnabled: Boolean = false,
    val isPinLockEnabled: Boolean = false,
    val showDetailedPingStats: Boolean = false,
    val isLoading: Boolean = true,
    val globalTunnelConfig: TunnelConfig? = null,
    val tunnels: List<TunnelConfig> = emptyList(),
    val monitoring: MonitoringSettings = MonitoringSettings(),
    val tunnelActive: Boolean = false,
)
