package com.zaneschepke.wireguardautotunnel.service.autotunnel

import com.zaneschepke.networkmonitor.ActiveNetwork
import com.zaneschepke.wireguardautotunnel.domain.events.AutoTunnelEvent
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.state.AutoTunnelState
import com.zaneschepke.wireguardautotunnel.util.extensions.isMatchingToWildcardList
import com.zaneschepke.wireguardautotunnel.util.extensions.transformWildcardsToRegex
import timber.log.Timber

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

        val isOnCaptivePortalWifi =
            network.activeNetwork is ActiveNetwork.Wifi &&
                network.activeNetwork.requiresCaptivePortalLogin

        if (isOnCaptivePortalWifi && settings.disableTunnelOnCaptivePortal) {
            return if (activeTunnelIds.isNotEmpty()) {
                Decision.Sync(start = emptySet(), stop = activeTunnelIds)
            } else {
                Decision.None
            }
        }

        if (!network.hasUsableNetwork) {
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
                listOfNotNull(
                    state.tunnels.firstOrNull { it.isEthernetTunnel } ?: defaultTunnel(state)
                )

            mobileActive && settings.isTunnelOnMobileDataEnabled ->
                listOfNotNull(
                    state.tunnels.firstOrNull { it.isMobileDataTunnel } ?: defaultTunnel(state)
                )

            wifiActive && settings.isTunnelOnWifiEnabled && !isWifiTrusted(state) ->
                findPreferredWifiTunnel(state)
            else -> emptyList()
        }
    }

    private fun findPreferredWifiTunnel(state: AutoTunnelState): List<TunnelConfig> {
        val wifi = state.networkState.activeNetwork as? ActiveNetwork.Wifi ?: return emptyList()
        val wildcardsEnabled = state.settings.isWildcardsEnabled

        // Highest priority is exact BSSID match
        val exactBssidMatches =
            state.tunnels.filter { tunnel -> tunnel.tunnelBSSIDs.contains(wifi.bssid) }
        if (exactBssidMatches.isNotEmpty()) {
            // One support for now
            val firstMatch = exactBssidMatches.first()
            Timber.i("Starting tunnel ${firstMatch.name} for exact BSSID match")
            return listOf()
        }

        // Next priority is SSID match
        val exactSsidMatches =
            state.tunnels.filter { tunnel -> tunnel.tunnelNetworks.contains(wifi.ssid) }

        if (exactSsidMatches.isNotEmpty()) {
            // One supported for now
            val firstMatch = exactSsidMatches.first()
            Timber.i("Starting tunnel ${firstMatch.name} for exact SSID match")
            return listOf(exactSsidMatches.first())
        }

        // Next priority is Wildcard BSSID match
        if (wildcardsEnabled) {
            val bestBssidMatch =
                findBestWildcardMatchStartTunnel(
                    tunnels = state.tunnels,
                    value = wifi.bssid,
                    getPatterns = { it.tunnelBSSIDs },
                )
            if (bestBssidMatch != null) {
                Timber.i("Starting tunnel ${bestBssidMatch.name} for BSSID wildcard match")
                return listOf(bestBssidMatch)
            }
        }

        // Next priority is SSID wildcard match
        if (wildcardsEnabled) {
            val bestSsidMatch =
                findBestWildcardMatchStartTunnel(
                    tunnels = state.tunnels,
                    value = wifi.ssid,
                    getPatterns = { it.tunnelNetworks },
                )
            if (bestSsidMatch != null) {
                Timber.i("Starting tunnel ${bestSsidMatch.name} for SSID wildcard match")
                return listOf(bestSsidMatch)
            }
        }

        // Fallback
        Timber.i("No preferred tunnel match, starting the default or first tunnel")
        return listOfNotNull(defaultTunnel(state))
    }

    private fun findBestWildcardMatchStartTunnel(
        tunnels: List<TunnelConfig>,
        value: String,
        getPatterns: (TunnelConfig) -> List<String>,
    ): TunnelConfig? {
        return tunnels
            .mapNotNull { tunnel ->
                val patterns = getPatterns(tunnel)

                // Check if we have any matches
                if (!patterns.isMatchingToWildcardList(value)) {
                    return@mapNotNull null
                }

                // Find the longest (most specific) pattern that matches
                val longestMatchingPatternLength =
                    patterns
                        .filter { pattern ->
                            // Don't consider exclude patterns
                            if (pattern.startsWith("!")) return@filter false

                            pattern.transformWildcardsToRegex().matches(value)
                        }
                        .maxOfOrNull { it.length } ?: 0

                tunnel to longestMatchingPatternLength
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun isWifiTrusted(state: AutoTunnelState): Boolean {
        val wifi = state.networkState.activeNetwork as? ActiveNetwork.Wifi ?: return false
        val settings = state.settings

        val ssidTrusted =
            matchesTrusted(
                value = wifi.ssid,
                trustedList = settings.trustedNetworkSSIDs,
                wildcardsEnabled = settings.isWildcardsEnabled,
            )

        val bssidTrusted =
            matchesTrusted(
                value = wifi.bssid,
                trustedList = settings.trustedNetworkBSSIDs,
                wildcardsEnabled = settings.isWildcardsEnabled,
            )

        return ssidTrusted || bssidTrusted
    }

    private fun matchesTrusted(
        value: String,
        trustedList: List<String>,
        wildcardsEnabled: Boolean,
    ): Boolean {
        if (trustedList.contains(value)) return true

        if (wildcardsEnabled && trustedList.isMatchingToWildcardList(value)) {
            return true
        }
        return false
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
