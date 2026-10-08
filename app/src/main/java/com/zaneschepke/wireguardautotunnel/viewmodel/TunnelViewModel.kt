package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.ipv6.IPv6Intent
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelUiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

class TunnelViewModel(
    private val tunnelRepository: TunnelRepository,
    private val tunnelCoordinator: TunnelCoordinator,
    val tunnelId: Int,
) : OrbitContainerHost<TunnelUiState, TunnelUiState, Nothing>, ViewModel() {

    override val container =
        orbitContainer<TunnelUiState, Nothing>(
            TunnelUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            combine(
                    tunnelRepository.userTunnelsFlow,
                    tunnelCoordinator.backendStatus.map { it.activeTunnels[tunnelId] },
                ) { tunnels, active ->
                    val tunnel = tunnels.firstOrNull { tun -> tun.id == tunnelId }
                    val config = tunnel?.getConfig()
                    val includedAppCount =
                        config?.`interface`?.includedApplications?.takeIf { it.isNotEmpty() }?.size

                    val excludedAppCount =
                        config?.`interface`?.excludedApplications?.takeIf { it.isNotEmpty() }?.size

                    state.copy(
                        tunnel = tunnel,
                        userTunnels = tunnels,
                        excludedAppsCount = excludedAppCount,
                        includedAppsCount = includedAppCount,
                        activeConfig = active?.activeConfig,
                        lastStatsAtMs = active?.lastStatsAtMs ?: 0L,
                        isLoading = false,
                    )
                }
                .collect { reduce { it } }
        }

    fun togglePrimaryTunnel() = intent {
        val tunnel = state.tunnel ?: return@intent
        val update = if (tunnel.isPrimaryTunnel) null else tunnel
        tunnelRepository.updatePrimaryTunnel(update)
    }

    fun onMetered(to: Boolean) = intent { tunnelRepository.setMetered(tunnelId, to) }

    fun onDDNSTunnel(to: Boolean) = intent { tunnelRepository.setDDNSTunnel(tunnelId, to) }

    fun onEntryTunnel(entryId: Int?) = intent {
        val tunnel = state.tunnel ?: return@intent
        if (entryId == tunnel.id) return@intent
        if (entryId != null) {
            val entry = state.userTunnels.firstOrNull { it.id == entryId } ?: return@intent
            if (entry.entryTunnelId != null) return@intent
        }
        val updated = tunnel.copy(entryTunnelId = entryId)
        tunnelRepository.save(updated)
        if (state.activeConfig != null) {
            tunnelCoordinator.stopTunnel(tunnelId)
            tunnelCoordinator.startTunnel(updated)
        }
    }

    fun onIPv6Action(iPv6Intent: IPv6Intent) = intent {
        val tunnel = state.tunnel ?: return@intent

        val updated =
            when (iPv6Intent) {
                is IPv6Intent.ToggleIpv6Preferred -> {
                    if (!iPv6Intent.value) {
                        tunnel.copy(isIpv6Preferred = false, ipv6RestoreEnabled = false)
                    } else {
                        tunnel.copy(isIpv6Preferred = true)
                    }
                }

                is IPv6Intent.ToggleRestore -> {
                    tunnel.copy(ipv6RestoreEnabled = iPv6Intent.value)
                }
            }

        tunnelRepository.save(updated)
    }
}
