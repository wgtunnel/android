package com.zaneschepke.wireguardautotunnel.core.service.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class TunnelControlTile : TileService() {

    private val tunnelsRepository: TunnelRepository by inject()
    private val tunnelCoordinator: TunnelCoordinator by inject()

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var observing = false

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        startObserving()
    }

    private fun startObserving() {
        if (observing) return
        observing = true

        tileScope.launch {
            val tunnels = withContext(Dispatchers.IO) { tunnelsRepository.getAll() }

            tunnelCoordinator.backendStatus
                .distinctUntilChangedBy { it.activeTunnels.keys }
                .collect { status ->
                    if (tunnels.isEmpty()) {
                        setUnavailable()
                        return@collect
                    }

                    val active = status.activeTunnels

                    if (active.isNotEmpty()) {
                        val names = tunnels.filter { active.containsKey(it.id) }.map { it.name }

                        setActive(names)
                    } else {
                        setInactive()
                    }
                }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        observing = false
    }

    override fun onClick() {
        unlockAndRun {
            tileScope.launch {
                tunnelCoordinator.toggleTunnels()
                updateTileState()
            }
        }
    }

    private fun updateTileState() {
        tileScope.launch {
            val tunnels = tunnelsRepository.getAll()

            if (tunnels.isEmpty()) {
                setUnavailable()
                return@launch
            }

            val active = tunnelCoordinator.backendStatus.value.activeTunnels

            if (active.isNotEmpty()) {
                val names = tunnels.filter { active.containsKey(it.id) }.map { it.name }

                setActive(names)
            } else {
                setInactive()
            }
        }
    }

    private fun setActive(names: List<String>) {
        val label =
            when {
                names.isEmpty() -> ""
                names.size == 1 -> names.first()
                names.size <= 3 -> names.joinToString(", ")
                else -> {
                    val visible = names.take(2).joinToString(", ")
                    "$visible +${names.size - 2}"
                }
            }

        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            subtitle = label
            updateTile()
        }
    }

    private fun setInactive() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            subtitle = ""
            updateTile()
        }
    }

    private fun setUnavailable() {
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            subtitle = ""
            updateTile()
        }
    }
}
