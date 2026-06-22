package com.zaneschepke.wireguardautotunnel.domain.state

import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
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
    fun matchesNetwork(ssid: String, candidates: Set<String>): Boolean {
        return if (settings.isWildcardsEnabled) {
            candidates.isMatchingToWildcardList(ssid)
        } else {
            candidates.contains(ssid)
        }
    }

    data class NetworkFingerprint(val transport: String, val ssid: String?)

    val networkFingerPrint: NetworkFingerprint
        get() =
            when (networkState.activeNetwork) {
                is ActiveNetwork.Wifi -> {
                    NetworkFingerprint(transport = "wifi", ssid = networkState.activeNetwork.ssid)
                }

                is ActiveNetwork.Cellular -> {
                    NetworkFingerprint(transport = "cellular", ssid = null)
                }

                is ActiveNetwork.Ethernet -> {
                    NetworkFingerprint(transport = "ethernet", ssid = null)
                }

                else -> {
                    NetworkFingerprint(transport = "none", ssid = null)
                }
            }
}
