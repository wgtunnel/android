package com.zaneschepke.wireguardautotunnel.core.service.tile

import android.content.Intent
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.lifecycle.*
import com.zaneschepke.wireguardautotunnel.core.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

class AutoTunnelControlTile : TileService(), LifecycleOwner {

    private val autoTunnelSettingsRepository: AutoTunnelSettingsRepository by inject()

    private val serviceManager: ServiceManager by inject()

    @OptIn(ExperimentalAtomicApi::class) val isCollecting = AtomicBoolean(false)

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

    override fun onStopListening() {
        super.onStopListening()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun initTileState() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        Timber.d("Start listening called for auto tunnel tile")
        if (isCollecting.compareAndSet(expectedValue = false, newValue = true)) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    serviceManager.autoTunnelService.collect {
                        if (it != null) return@collect setActive()
                        setInactive()
                    }
                }
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        initTileState()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            lifecycleScope.launch {
                if (serviceManager.autoTunnelService.value != null) {
                    autoTunnelSettingsRepository.updateAutoTunnelEnabled(false)
                    setInactive()
                } else {
                    autoTunnelSettingsRepository.updateAutoTunnelEnabled(true)
                    setActive()
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

    /* This works around an annoying unsolved frameworks bug some people are hitting. */
    override fun onBind(intent: Intent): IBinder? {
        var ret: IBinder? = null
        try {
            ret = super.onBind(intent)
        } catch (_: Throwable) {
            Timber.e("Failed to bind to AutoTunnelControlTile")
        }
        return ret
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
}
