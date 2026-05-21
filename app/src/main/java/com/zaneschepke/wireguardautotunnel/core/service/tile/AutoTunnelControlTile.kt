package com.zaneschepke.wireguardautotunnel.core.service.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zaneschepke.wireguardautotunnel.core.orchestration.AutoTunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.service.autotunnel.AutoTunnelStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AutoTunnelControlTile : TileService() {

    private val autoTunnelStateHolder: AutoTunnelStateHolder by inject()
    private val autoTunnelCoordinator: AutoTunnelCoordinator by inject()

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        observeState()
    }

    override fun onStopListening() {
        tileScope.coroutineContext.cancelChildren()
    }

    override fun onClick() {
        unlockAndRun { tileScope.launch { autoTunnelCoordinator.toggle() } }
    }

    private fun observeState() {
        tileScope.launch {
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
