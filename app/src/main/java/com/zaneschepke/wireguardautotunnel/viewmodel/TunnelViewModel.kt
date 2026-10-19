package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.wgtunnel.backend.state.ActiveTunnel
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.ipv6.IPv6Intent
import com.zaneschepke.wireguardautotunnel.ui.state.AppliedSplitApps
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelUiState
import com.zaneschepke.wireguardautotunnel.ui.state.splitAppListSummary
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

class TunnelViewModel(
    private val tunnelRepository: TunnelRepository,
    private val tunnelCoordinator: TunnelCoordinator,
    private val settingsRepository: GeneralSettingRepository,
    val tunnelId: Int,
) : OrbitContainerHost<TunnelUiState, TunnelUiState, Nothing>, ViewModel() {

    override val container =
        orbitContainer<TunnelUiState, Nothing>(
            TunnelUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            combine(
                    tunnelRepository.userTunnelsFlow,
                    tunnelRepository.globalTunnelFlow,
                    settingsRepository.flow,
                    tunnelCoordinator.backendStatus.map { it.activeTunnels[tunnelId] },
                ) { tunnels, global, settings, active ->
                    TunnelSplitSnapshot(tunnels, global, settings, active)
                }
                .collect { snapshot ->
                    reduce {
                        val tunnel = snapshot.tunnels.firstOrNull { tun -> tun.id == tunnelId }
                        state.copy(
                            tunnel = tunnel,
                            userTunnels = snapshot.tunnels,
                            appliedSplit = appliedSplit(tunnel, snapshot.global, snapshot.settings),
                            activeConfig = snapshot.active?.activeConfig,
                            lastStatsAtMs = snapshot.active?.lastStatsAtMs ?: 0L,
                            isLoading = false,
                        )
                    }
                }
        }

    private fun appliedSplit(
        tunnel: TunnelConfig?,
        global: TunnelConfig?,
        settings: GeneralSettings,
    ): AppliedSplitApps? {
        // A missing global tunnel leaves this tunnel's own lists in effect.
        val managedGlobally = settings.isGlobalSplitTunnelEnabled && global != null
        val source = if (managedGlobally) global else tunnel
        val iface = source?.getConfig()?.`interface`
        val summary =
            splitAppListSummary(
                includedCount = iface?.allIncludedApps?.size ?: 0,
                excludedCount = iface?.allExcludedApps?.size ?: 0,
                emptyMeansAllTunneled = managedGlobally,
            ) ?: return null
        return AppliedSplitApps(summary = summary, managedGlobally = managedGlobally)
    }

    private data class TunnelSplitSnapshot(
        val tunnels: List<TunnelConfig>,
        val global: TunnelConfig?,
        val settings: GeneralSettings,
        val active: ActiveTunnel?,
    )

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
