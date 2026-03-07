package com.zaneschepke.wireguardautotunnel.core.tunnel.handler

import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class TunnelActiveStatePersister(
    private val activeTunnels: StateFlow<Map<Int, TunnelState>>,
    private val tunnelsRepository: TunnelRepository,
    applicationScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
) {
    private var previousActiveIds: Set<Int> = emptySet()

    init {
        applicationScope.launch(ioDispatcher) {
            activeTunnels.collect { currentActive ->
                val currentActiveIds = currentActive.keys
                if (currentActiveIds == previousActiveIds) return@collect

                val tunnels = tunnelsRepository.userTunnelsFlow.firstOrNull() ?: return@collect
                val tunnelsById = tunnels.associateBy { it.id }

                val relevantIds = previousActiveIds + currentActiveIds

                supervisorScope {
                    relevantIds.forEach { id ->
                        launch {
                            val config = tunnelsById[id] ?: return@launch
                            val wasActive = previousActiveIds.contains(id)
                            val isActive = currentActiveIds.contains(id)
                            if (wasActive != isActive) {
                                tunnelsRepository.save(config.copy(isActive = isActive))
                            }
                        }
                    }
                }
                previousActiveIds = currentActiveIds.toSet()
            }
        }
    }
}
