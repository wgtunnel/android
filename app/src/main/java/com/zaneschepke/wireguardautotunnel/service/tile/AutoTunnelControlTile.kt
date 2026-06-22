package com.zaneschepke.wireguardautotunnel.service.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zaneschepke.wireguardautotunnel.core.orchestration.AutoTunnelCoordinator
import com.zaneschepke.wireguardautotunnel.service.autotunnel.AutoTunnelStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AutoTunnelControlTile : TileService() {

    private val autoTunnelStateHolder: AutoTunnelStateHolder by inject()
    private val autoTunnelCoordinator: AutoTunnelCoordinator by inject()

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var collectionJob: Job? = null

    override fun onDestroy() {
        collectionJob?.cancel()
        collectionJob = null
        tileScope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
        startObserving()
    }

    override fun onStopListening() {
        super.onStopListening()
        collectionJob?.cancel()
        collectionJob = null
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
        startObserving()
    }

    override fun onClick() {
        unlockAndRun {
            tileScope.launch {
                autoTunnelCoordinator.toggle()
                updateTileState()
            }
        }
    }

    private fun updateTileState() {
        val isActive = autoTunnelStateHolder.active.value
        if (isActive) setActive() else setInactive()
    }

    private fun startObserving() {
        collectionJob?.cancel()
        collectionJob = tileScope.launch {
            autoTunnelStateHolder.active.collect { active ->
                if (active) setActive() else setInactive()
            }
        }
    }

    private fun setActive() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    private fun setInactive() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
