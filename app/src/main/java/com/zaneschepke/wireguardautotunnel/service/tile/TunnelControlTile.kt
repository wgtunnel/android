package com.zaneschepke.wireguardautotunnel.service.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class TunnelControlTile : TileService() {

    private val tunnelsRepository: TunnelRepository by inject()
    private val tunnelCoordinator: TunnelCoordinator by inject()

    private var collectionJob: Job? = null

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onDestroy() {
        collectionJob?.cancel()
        collectionJob = null
        tileScope.cancel()
        super.onDestroy()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
        startObserving()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
        startObserving()
    }

    private fun startObserving() {
        collectionJob?.cancel()
        collectionJob = tileScope.launch {
            combine(
                    tunnelsRepository.userTunnelsFlow.distinctUntilChanged(),
                    tunnelCoordinator.backendStatus.distinctUntilChangedBy { it.activeTunnels.keys },
                ) { tunnels, status ->
                    if (tunnels.isEmpty()) {
                        setUnavailable()
                        return@combine
                    }

                    val active = status.activeTunnels
                    if (active.isNotEmpty()) {
                        val names = tunnels.filter { active.containsKey(it.id) }.map { it.name }
                        setActive(names)
                    } else {
                        setInactive()
                    }
                }
                .collect()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        collectionJob?.cancel()
        collectionJob = null
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
            val tunnels = withContext(Dispatchers.IO) { tunnelsRepository.getAll() }

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = label
            }
            contentDescription = label

            updateTile()
        }
    }

    private fun setInactive() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = ""
            }
            contentDescription = ""

            updateTile()
        }
    }

    private fun setUnavailable() {
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = ""
            }
            contentDescription = ""

            updateTile()
        }
    }
}
