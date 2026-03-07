package com.zaneschepke.wireguardautotunnel.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.di.Scope
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import timber.log.Timber

class RemoteControlReceiver : BroadcastReceiver(), KoinComponent {

    private val applicationScope: CoroutineScope by inject(named(Scope.APPLICATION))
    private val settingsRepository: GeneralSettingRepository by inject()
    private val tunnelsRepository: TunnelRepository by inject()
    private val autoTunnelSettingsRepository: AutoTunnelSettingsRepository by inject()
    private val tunnelManager: TunnelManager by inject()

    enum class Action(private val suffix: String) {
        START_TUNNEL("START_TUNNEL"),
        STOP_TUNNEL("STOP_TUNNEL"),
        START_AUTO_TUNNEL("START_AUTO_TUNNEL"),
        STOP_AUTO_TUNNEL("STOP_AUTO_TUNNEL");

        fun getFullAction(): String {
            return "${Constants.BASE_PACKAGE}.$suffix"
        }

        companion object {
            fun fromAction(action: String): Action? {
                for (a in entries) {
                    if (a.getFullAction() == action) {
                        return a
                    }
                }
                return null
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("onReceive")
        val action = intent.action ?: return
        val appAction = Action.fromAction(action) ?: return Timber.w("Unknown action $action")
        applicationScope.launch {
            val settings = settingsRepository.getGeneralSettings()
            if (!settings.isRemoteControlEnabled) return@launch Timber.w("Remote control disabled")
            val key = settings.remoteKey ?: return@launch Timber.w("Remote control key missing")
            if (key != intent.getStringExtra(EXTRA_KEY)?.trim())
                return@launch Timber.w("Invalid remote control key")
            when (appAction) {
                Action.START_TUNNEL -> {
                    val tunnelName =
                        intent.getStringExtra(EXTRA_TUN_NAME) ?: return@launch startDefaultTunnel()
                    val tunnel =
                        tunnelsRepository.findByTunnelName(tunnelName)
                            ?: return@launch startDefaultTunnel()
                    tunnelManager.startTunnel(tunnel)
                }
                Action.STOP_TUNNEL -> {
                    val tunnelName =
                        intent.getStringExtra(EXTRA_TUN_NAME)
                            ?: return@launch tunnelManager.stopActiveTunnels()
                    val tunnel =
                        tunnelsRepository.findByTunnelName(tunnelName)
                            ?: return@launch tunnelManager.stopActiveTunnels()
                    tunnelManager.stopTunnel(tunnel.id)
                }
                Action.START_AUTO_TUNNEL ->
                    autoTunnelSettingsRepository.updateAutoTunnelEnabled(true)
                Action.STOP_AUTO_TUNNEL ->
                    autoTunnelSettingsRepository.updateAutoTunnelEnabled(false)
            }
        }
    }

    private suspend fun startDefaultTunnel() {
        tunnelsRepository.getDefaultTunnel()?.let { tunnel -> tunnelManager.startTunnel(tunnel) }
    }

    companion object {
        const val EXTRA_TUN_NAME = "tunnelName"
        const val EXTRA_KEY = "key"
    }
}
