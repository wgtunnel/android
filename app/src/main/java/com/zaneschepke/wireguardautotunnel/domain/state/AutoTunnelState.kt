package com.zaneschepke.wireguardautotunnel.domain.state

import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.core.service.autotunnel.BackendStatusChange
import com.zaneschepke.wireguardautotunnel.core.service.autotunnel.NetworkChange
import com.zaneschepke.wireguardautotunnel.core.service.autotunnel.SettingsChange
import com.zaneschepke.wireguardautotunnel.core.service.autotunnel.StateChange
import com.zaneschepke.wireguardautotunnel.data.model.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.events.AutoTunnelEvent
import com.zaneschepke.wireguardautotunnel.domain.events.AutoTunnelEvent.DoNothing
import com.zaneschepke.wireguardautotunnel.domain.events.AutoTunnelEvent.Start
import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.util.extensions.isMatchingToWildcardList

data class AutoTunnelState(
    val backendStatus: BackendStatus = BackendStatus(),
    val networkState: NetworkState = NetworkState(),
    val settings: AutoTunnelSettings = AutoTunnelSettings(),
    val tunnelMode: TunnelMode = TunnelMode.VPN,
    val tunnels: List<TunnelConfig> = emptyList(),
) {
    fun matchesNetwork(
        ssid: String,
        candidates: Set<String>
    ): Boolean {
        return if (settings.isWildcardsEnabled) {
            candidates.isMatchingToWildcardList(ssid)
        } else {
            candidates.contains(ssid)
        }
    }
}
