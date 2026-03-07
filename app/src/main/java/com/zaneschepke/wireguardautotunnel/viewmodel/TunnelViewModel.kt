package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelUiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class TunnelViewModel(
    private val tunnelRepository: TunnelRepository,
    private val tunnelManager: TunnelManager,
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
                    tunnelManager.activeTunnels.map { it.containsKey(tunnelId) },
                ) { tunnel, active ->
                    state.copy(tunnel = tunnel, isActive = active, isLoading = false)
                }
                .collect { reduce { it } }
        }

    fun setRestartOnPing(to: Boolean) = intent {
        val tunnel = state.tunnel ?: return@intent
        tunnelRepository.save(tunnel.copy(restartOnPingFailure = to))
    }

    fun togglePrimaryTunnel() = intent {
        val tunnel = state.tunnel ?: return@intent
        val update = if (tunnel.isPrimaryTunnel) null else tunnel
        tunnelRepository.updatePrimaryTunnel(update)
    }

    fun setIpv4Preferred(to: Boolean) = intent {
        val tunnel = state.tunnel ?: return@intent
        tunnelRepository.save(tunnel.copy(isIpv4Preferred = to))
    }

    fun setMetered(to: Boolean) = intent {
        val tunnel = state.tunnel ?: return@intent
        tunnelRepository.save(tunnel.copy(isMetered = to))
    }
}
