package com.zaneschepke.wireguardautotunnel.domain.state

import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

data class AutoTunnelState(
    val backendStatus: BackendStatus = BackendStatus(),
    val networkState: NetworkState = NetworkState(),
    val settings: AutoTunnelSettings = AutoTunnelSettings(),
    val tunnelMode: TunnelMode = TunnelMode.VPN,
    val tunnels: List<TunnelConfig> = emptyList(),
)
