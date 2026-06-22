package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.ipv6.IPv6Intent
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelUiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class TunnelViewModel(
    private val tunnelRepository: TunnelRepository,
    private val tunnelCoordinator: TunnelCoordinator,
    val tunnelId: Int,
) : ContainerHost<TunnelUiState, Nothing>, ViewModel() {

    override val container =
        container<TunnelUiState, Nothing>(
            TunnelUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            combine(
                    tunnelRepository.userTunnelsFlow.map {
                        it.firstOrNull { tun -> tun.id == tunnelId }
                    },
                    tunnelCoordinator.backendStatus.map { it.activeTunnels[tunnelId] },
                ) { tunnel, active ->
                    val config = tunnel?.getConfig()
                    val includedAppCount =
                        config?.`interface`?.includedApplications?.takeIf { it.isNotEmpty() }?.size

                    val excludedAppCount =
                        config?.`interface`?.excludedApplications?.takeIf { it.isNotEmpty() }?.size

                    state.copy(
                        tunnel = tunnel,
                        excludedAppsCount = excludedAppCount,
                        includedAppsCount = includedAppCount,
                        activeConfig = active?.activeConfig,
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

    fun onDynamicDns(to: Boolean) = intent { tunnelRepository.setDynamicDns(tunnelId, to) }

    fun onMetered(to: Boolean) = intent { tunnelRepository.setMetered(tunnelId, to) }

    fun onIPv6Action(iPv6Intent: IPv6Intent) = intent {
        val tunnel = state.tunnel ?: return@intent

        val updated =
            when (iPv6Intent) {
                is IPv6Intent.ToggleFallback -> {
                    tunnel.copy(ipv4FallbackEnabled = iPv6Intent.value)
                }

                is IPv6Intent.ToggleIpv6Preferred -> {
                    if (!iPv6Intent.value) {
                        tunnel.copy(
                            isIpv6Preferred = false,
                            ipv6RestoreEnabled = false,
                            ipv4FallbackEnabled = false,
                        )
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
