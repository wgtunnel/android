package com.zaneschepke.wireguardautotunnel.domain.state

import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

data class AutoTunnelState(
    val networkState: NetworkState = NetworkState(),
    val settings: AutoTunnelSettings = AutoTunnelSettings(),
    val tunnelMode: TunnelMode = TunnelMode.VPN,
    val tunnels: List<TunnelConfig> = emptyList(),
    // Debounced separately from networkState.activeNetwork's raw captive-portal capability.
    // Android's captive portal can flap on flaky networks, so this only reports
    // false once the raw signal has held false for a certain duration.
    val confirmedCaptivePortal: Boolean = false,
    // Debounced separately from networkState.hasUsableNetwork. Android's connectivity
    // validation can flap true/false for an extended period during network transitions
    val confirmedHasUsableNetwork: Boolean = false,
)
