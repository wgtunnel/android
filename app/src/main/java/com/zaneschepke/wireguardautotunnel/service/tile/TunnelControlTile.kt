package com.zaneschepke.wireguardautotunnel.service.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.wgtunnel.backend.state.ActiveTunnel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class TunnelControlTile : TileService() {

    private val tunnelsRepository: TunnelRepository by inject()
    private val tunnelCoordinator: TunnelCoordinator by inject()

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        unlockAndRun {
            tileScope.launch {
                tunnelCoordinator.toggleActiveTunnels()
                updateTileState()
            }
        }
    }

    private fun updateTileState() {
        tileScope.launch {
            val tunnels =
                withContext(Dispatchers.IO) { tunnelsRepository.userTunnelsFlow.firstOrNull() }

            if (tunnels.isNullOrEmpty()) {
                setUnavailable()
                return@launch
            }

            val active = tunnelCoordinator.backendStatus.value.activeTunnels

            if (active.isNotEmpty()) {
                val activeMap =
                    tunnels
                        .filter { active.containsKey(it.id) }
                        .associate { tunnel -> tunnel.name to active.getValue(tunnel.id) }
                setActive(activeMap)
            } else {
                setInactive()
            }
        }
    }

    private fun setActive(activeByName: Map<String, ActiveTunnel>) {
        val context = this
        qsTile?.apply {
            state = Tile.STATE_ACTIVE

            when (activeByName.size) {
                1 -> {
                    val (fullName, activeTunnel) = activeByName.entries.first()
                    val state = DisplayTunnelState.from(activeTunnel).asLocalizedString(context)

                    label = fullName
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        subtitle = state
                    }
                    contentDescription = "$fullName • $state}"
                }

                else -> {
                    val tunnels = getString(R.string.tunnels).lowercase(Locale.getDefault())
                    label = "${activeByName.size} $tunnels"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        subtitle = ""
                    }
                    contentDescription = "${activeByName.size} $tunnels"
                }
            }
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
            label = getString(R.string.tunnel_control)
            state = Tile.STATE_UNAVAILABLE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = ""
            }
            contentDescription = ""

            updateTile()
        }
    }
}
