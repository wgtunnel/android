package com.zaneschepke.wireguardautotunnel.service.autotunnel

import com.wgtunnel.backend.autotunnel.AutoTunnelNetwork
import com.wgtunnel.backend.autotunnel.AutoTunnelNetworkType
import com.wgtunnel.backend.autotunnel.AutoTunnelPolicy
import com.wgtunnel.backend.autotunnel.AutoTunnelSnapshot
import com.wgtunnel.backend.autotunnel.AutoTunnelTunnel
import com.zaneschepke.networkmonitor.ActiveNetwork
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.state.AutoTunnelState

fun AutoTunnelState.toCore(): AutoTunnelSnapshot {
    val wifi = networkState.activeNetwork as? ActiveNetwork.Wifi
    return AutoTunnelSnapshot(
        network =
            AutoTunnelNetwork(
                type =
                    when (networkState.activeNetwork) {
                        is ActiveNetwork.Wifi -> AutoTunnelNetworkType.WIFI
                        is ActiveNetwork.Ethernet -> AutoTunnelNetworkType.ETHERNET
                        is ActiveNetwork.Cellular -> AutoTunnelNetworkType.CELLULAR
                        is ActiveNetwork.Disconnected -> AutoTunnelNetworkType.DISCONNECTED
                    },
                ssid = wifi?.ssid.orEmpty(),
                bssid = wifi?.bssid.orEmpty(),
                hasUsableNetwork = confirmedHasUsableNetwork,
                // Debounced. Reacts to detected immediately but only cleared once false has
                // held stable for a duration
                captivePortal = wifi != null && confirmedCaptivePortal,
            ),
        policy =
            AutoTunnelPolicy(
                isTunnelOnWifiEnabled = settings.isTunnelOnWifiEnabled,
                isTunnelOnEthernetEnabled = settings.isTunnelOnEthernetEnabled,
                isTunnelOnMobileDataEnabled = settings.isTunnelOnMobileDataEnabled,
                isWildcardsEnabled = settings.isWildcardsEnabled,
                isStopOnNoInternetEnabled = settings.isStopOnNoInternetEnabled,
                disableTunnelOnCaptivePortal = settings.disableTunnelOnCaptivePortal,
                trustedNetworkSsids = settings.trustedNetworkSSIDs,
                trustedNetworkBssids = settings.trustedNetworkBSSIDs,
            ),
        tunnels = tunnels.map { it.toCore() },
    )
}

private fun TunnelConfig.toCore(): AutoTunnelTunnel {
    return AutoTunnelTunnel(
        id = id.toLong(),
        name = name,
        isPrimaryTunnel = isPrimaryTunnel,
        isEthernetTunnel = isEthernetTunnel,
        isMobileDataTunnel = isMobileDataTunnel,
        tunnelNetworks = tunnelNetworks,
        tunnelBssids = tunnelBSSIDs,
    )
}
