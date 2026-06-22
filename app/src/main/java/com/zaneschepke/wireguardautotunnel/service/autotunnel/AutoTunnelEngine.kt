package com.zaneschepke.wireguardautotunnel.service.autotunnel

import com.zaneschepke.wireguardautotunnel.domain.events.AutoTunnelEvent
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.state.ActiveNetwork
import com.zaneschepke.wireguardautotunnel.domain.state.AutoTunnelState

class AutoTunnelEngine {

    fun evaluate(state: AutoTunnelState): AutoTunnelEvent {
        return when (val decision = decide(state)) {
            is Decision.Sync -> {
                if (decision.start.isEmpty() && decision.stop.isEmpty()) {
                    AutoTunnelEvent.DoNothing
                } else {
                    AutoTunnelEvent.Sync(start = decision.start, stop = decision.stop)
                }
            }
            Decision.None -> AutoTunnelEvent.DoNothing
            is Decision.StopDueToNoInternet -> AutoTunnelEvent.StopAllDueToNoInternet
        }
    }

    private fun decide(state: AutoTunnelState): Decision {
        val network = state.networkState
        val settings = state.settings
        val backend = state.backendStatus

        val activeTunnelIds = backend.activeTunnels.keys.toSet()

        if (!network.hasInternet()) {
            return if (settings.isStopOnNoInternetEnabled) {
                Decision.StopDueToNoInternet
            } else {
                // keep tunnel state neutral on no internet otherwise
                Decision.None
            }
        }

        val desiredTunnels = resolveDesiredTunnels(state).map { it.id }.toSet()

        val toStart = desiredTunnels - activeTunnelIds
        val toStop = activeTunnelIds - desiredTunnels

        if (toStart.isEmpty() && toStop.isEmpty()) {
            return Decision.None
        }

        return Decision.Sync(
            start = state.tunnels.filter { it.id in toStart }.toSet(),
            stop = toStop,
        )
    }

    private fun resolveDesiredTunnels(state: AutoTunnelState): List<TunnelConfig> {
        val network = state.networkState
        val settings = state.settings

        val wifiActive = network.activeNetwork is ActiveNetwork.Wifi
        val mobileActive = network.activeNetwork is ActiveNetwork.Cellular
        val ethernetActive = network.activeNetwork is ActiveNetwork.Ethernet

        return when {
            ethernetActive && settings.isTunnelOnEthernetEnabled ->
                resolveByPriority(state) { it.isEthernetTunnel }

            mobileActive && settings.isTunnelOnMobileDataEnabled ->
                resolveByPriority(state) { it.isMobileDataTunnel }

            wifiActive && settings.isTunnelOnWifiEnabled && !isWifiTrusted(state) ->
                resolveWifiTunnels(state)
            else -> emptyList()
        }
    }

    private fun resolveByPriority(
        state: AutoTunnelState,
        predicate: (TunnelConfig) -> Boolean,
    ): List<TunnelConfig> {
        return listOfNotNull(state.tunnels.firstOrNull(predicate) ?: defaultTunnel(state))
    }

    private fun resolveWifiTunnels(state: AutoTunnelState): List<TunnelConfig> {
        val wifi = state.networkState.activeNetwork as? ActiveNetwork.Wifi ?: return emptyList()

        val matched = state.tunnels.filter { state.matchesNetwork(wifi.ssid, it.tunnelNetworks) }

        return matched.ifEmpty { listOfNotNull(defaultTunnel(state)) }
    }

    private fun isWifiTrusted(state: AutoTunnelState): Boolean {
        val wifi = state.networkState.activeNetwork as? ActiveNetwork.Wifi ?: return false
        return state.matchesNetwork(wifi.ssid, state.settings.trustedNetworkSSIDs)
    }

    private fun defaultTunnel(state: AutoTunnelState): TunnelConfig? {
        return state.tunnels.firstOrNull { it.isPrimaryTunnel } ?: state.tunnels.firstOrNull()
    }

    private sealed interface Decision {
        data class Sync(val start: Set<TunnelConfig>, val stop: Set<Int>) : Decision

        data object None : Decision

        data object StopDueToNoInternet : Decision
    }
}
