package com.zaneschepke.wireguardautotunnel.domain.state

import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.core.service.autotunnel.BackendStatusChange
import com.zaneschepke.wireguardautotunnel.core.service.autotunnel.NetworkChange
import com.zaneschepke.wireguardautotunnel.core.service.autotunnel.SettingsChange
import com.zaneschepke.wireguardautotunnel.core.service.autotunnel.StateChange
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
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
    val appMode: AppMode = AppMode.VPN,
    val tunnels: List<TunnelConfig> = emptyList(),
) {

    fun determineAutoTunnelEvent(stateChange: StateChange): AutoTunnelEvent {
        when (stateChange) {
            is NetworkChange,
            is SettingsChange -> {
                // Compute desired tunnel based on network conditions
                var preferredTunnel: TunnelConfig? = null
                if (ethernetActive && settings.isTunnelOnEthernetEnabled) {
                    preferredTunnel = preferredEthernetTunnel()
                } else if (mobileDataActive && settings.isTunnelOnMobileDataEnabled) {
                    preferredTunnel = preferredMobileDataTunnel()
                } else if (wifiActive && settings.isTunnelOnWifiEnabled && !isWifiTrusted()) {
                    preferredTunnel = preferredWifiTunnel()
                }

                // Override for no connectivity if enabled
                if (!networkState.hasInternet() && settings.isStopOnNoInternetEnabled) {
                    preferredTunnel = null
                }

                // Determine current active tunnel (assuming only one can be active)
                val currentTunnel = backendStatus.activeTunnels.entries.firstOrNull()?.key

                // Handle tunnel start/stop/change
                if (preferredTunnel != null) {
                    if (currentTunnel != preferredTunnel.id) {
                        return Start(preferredTunnel)
                    }
                } else {
                    if (currentTunnel != null) {
                        return AutoTunnelEvent.Stop
                    }
                }
            }

            is BackendStatusChange -> Unit
        }
        return DoNothing
    }

    private val ethernetActive: Boolean = networkState.activeNetwork is ActiveNetwork.Ethernet
    private val mobileDataActive: Boolean = networkState.activeNetwork is ActiveNetwork.Cellular
    private val wifiActive: Boolean = networkState.activeNetwork is ActiveNetwork.Wifi

    private fun preferredMobileDataTunnel(): TunnelConfig? {
        return tunnels.firstOrNull { it.isMobileDataTunnel }
            ?: tunnels.firstOrNull { it.isPrimaryTunnel }
            ?: tunnels.firstOrNull()
    }

    private fun preferredEthernetTunnel(): TunnelConfig? {
        return tunnels.firstOrNull { it.isEthernetTunnel }
            ?: tunnels.firstOrNull { it.isPrimaryTunnel }
            ?: tunnels.firstOrNull()
    }

    private fun preferredWifiTunnel(): TunnelConfig? {
        return getTunnelWithMappedNetwork()
            ?: tunnels.firstOrNull { it.isPrimaryTunnel }
            ?: tunnels.firstOrNull()
    }

    private fun isWifiTrusted(): Boolean {
        return with(networkState.activeNetwork) {
            this is ActiveNetwork.Wifi && isTrustedNetwork(this.ssid)
        }
    }

    private fun isTrustedNetwork(ssid: String): Boolean =
        hasMatch(ssid, settings.trustedNetworkSSIDs)

    private fun hasMatch(
        wifiName: String,
        wifiNames: Set<String> = settings.trustedNetworkSSIDs,
    ): Boolean {
        return if (settings.isWildcardsEnabled) {
            wifiNames.isMatchingToWildcardList(wifiName)
        } else {
            wifiNames.contains(wifiName)
        }
    }

    private fun getTunnelWithMappedNetwork(): TunnelConfig? =
        when (val network = networkState.activeNetwork) {
            is ActiveNetwork.Wifi ->
                tunnels.firstOrNull { hasMatch(network.ssid, it.tunnelNetworks) }
            else -> null
        }
}
