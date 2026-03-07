package com.zaneschepke.wireguardautotunnel.core.service.tile

import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.WireGuardAutoTunnel
import com.zaneschepke.wireguardautotunnel.core.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.android.inject
import timber.log.Timber

class TunnelControlTile : TileService(), LifecycleOwner {

    private val tunnelsRepository: TunnelRepository by inject()

    private val serviceManager: ServiceManager by inject()

    private val tunnelManager: TunnelManager by inject()

    @OptIn(ExperimentalAtomicApi::class) val isCollecting = AtomicBoolean(false)

    private val startLock = Mutex()

    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        initTileState()
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun initTileState() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        Timber.d("Start listening called for tunnel tile")
        if (isCollecting.compareAndSet(expectedValue = false, newValue = true)) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    tunnelManager.activeTunnels
                        .distinctUntilChangedBy { it.size }
                        .collect { updateTileState() }
                }
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        initTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    private suspend fun updateTileState() {
        try {
            val tunnels = tunnelsRepository.getAll()
            if (tunnels.isEmpty()) {
                setUnavailable()
                return
            }

            val activeTunnels =
                tunnelManager.activeTunnels.value.filter { it.value.status.isUpOrStarting() }

            when {
                activeTunnels.isNotEmpty() -> {
                    val activeIds = activeTunnels.map { it.key }
                    // TODO improvements would be needed to make this work well with toggling
                    // multiple tunnels
                    // this would be better managed elsewhere
                    WireGuardAutoTunnel.setLastActiveTunnels(activeIds)
                    val activeTunNames =
                        tunnels.filter { activeTunnels.keys.contains(it.id) }.map { it.name }
                    updateTileForActiveTunnels(activeTunNames)
                }
                else -> updateTileForLastActiveTunnels()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update tunnel state")
            setUnavailable()
        }
    }

    private fun updateTileForActiveTunnels(activeTunnelNames: List<String>) {
        val tileName =
            when (activeTunnelNames.size) {
                1 -> activeTunnelNames[0]
                else -> getString(R.string.multiple)
            }
        updateTile(tileName, true)
    }

    private suspend fun updateTileForLastActiveTunnels() {
        val lastActiveIds = WireGuardAutoTunnel.getLastActiveTunnels()
        when {
            lastActiveIds.isEmpty() -> {
                tunnelsRepository.getStartTunnel()?.let { config -> updateTile(config.name, false) }
                    ?: setUnavailable()
            }
            lastActiveIds.size > 1 -> updateTile(getString(R.string.multiple), false)
            else -> {
                val tunnelId = lastActiveIds.first()
                tunnelsRepository.getById(tunnelId)?.let { tunnel ->
                    updateTile(tunnel.name, false)
                } ?: setUnavailable()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            lifecycleScope.launch {
                startLock.withLock {
                    if (tunnelManager.activeTunnels.value.isNotEmpty())
                        return@launch tunnelManager.stopActiveTunnels()
                    val lastActive = WireGuardAutoTunnel.getLastActiveTunnels()
                    if (lastActive.isEmpty()) {
                        tunnelsRepository.getStartTunnel()?.let { tunnelManager.startTunnel(it) }
                    } else {
                        lastActive.forEach { id ->
                            tunnelsRepository.getById(id)?.let { tunnelManager.startTunnel(it) }
                        }
                    }
                }
            }
        }
    }

    private fun setActive() {
        qsTile?.let {
            it.state = Tile.STATE_ACTIVE
            it.updateTile()
        }
    }

    private fun setInactive() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    private fun setUnavailable() {
        qsTile?.let {
            it.state = Tile.STATE_UNAVAILABLE
            setTileDescription("")
            it.updateTile()
        }
    }

    private fun setTileDescription(description: String) {
        qsTile?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                it.subtitle = description
                it.stateDescription = description
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.subtitle = description
            }
            it.updateTile()
        }
    }

    private fun updateTile(name: String, active: Boolean) {
        runCatching {
                setTileDescription(name)
                if (active) return setActive()
                setInactive()
            }
            .onFailure { Timber.e(it) }
    }

    /* This works around an annoying unsolved frameworks bug some people are hitting. */
    override fun onBind(intent: Intent): IBinder? {
        var ret: IBinder? = null
        try {
            ret = super.onBind(intent)
        } catch (_: Throwable) {
            Timber.e("Failed to bind to TunnelControlTile")
        }
        return ret
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
}
