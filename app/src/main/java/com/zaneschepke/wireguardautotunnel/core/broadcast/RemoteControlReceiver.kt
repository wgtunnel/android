package com.zaneschepke.wireguardautotunnel.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zaneschepke.wireguardautotunnel.core.orchestration.AutoTunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.di.Scope
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class RemoteControlReceiver : BroadcastReceiver(), KoinComponent {

    private val applicationScope: CoroutineScope by inject(named(Scope.APPLICATION))

    private val settingsRepository: GeneralSettingRepository by inject()
    private val tunnelsRepository: TunnelRepository by inject()
    private val tunnelCoordinator: TunnelCoordinator by inject()
    private val autoTunnelCoordinator: AutoTunnelCoordinator by inject()

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
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                val action = intent.action ?: return@launch
                val appAction = Action.fromAction(action) ?: return@launch

                val settings = settingsRepository.getGeneralSettings()

                if (!settings.isRemoteControlEnabled) return@launch

                if (!validateKey(settings, intent)) return@launch

                when (appAction) {
                    Action.START_TUNNEL -> {
                        val tunnel =
                            resolveTunnel(intent)
                                ?: tunnelsRepository.getDefaultTunnel()
                                ?: return@launch

                        tunnelCoordinator.startTunnel(tunnel)
                    }

                    Action.STOP_TUNNEL -> {
                        val tunnelName = intent.getStringExtra(EXTRA_TUN_NAME)

                        if (tunnelName == null) {
                            tunnelCoordinator.stopActiveTunnels()
                            return@launch
                        }

                        val tunnel = tunnelsRepository.findByTunnelName(tunnelName) ?: return@launch

                        tunnelCoordinator.stopTunnel(tunnel.id)
                    }

                    Action.START_AUTO_TUNNEL -> {
                        autoTunnelCoordinator.enable()
                    }

                    Action.STOP_AUTO_TUNNEL -> {
                        autoTunnelCoordinator.disable()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun validateKey(settings: GeneralSettings, intent: Intent): Boolean {

        val expected = settings.remoteKey?.trim() ?: return false

        val actual = intent.getStringExtra(EXTRA_KEY)?.trim()

        return expected == actual
    }

    private suspend fun resolveTunnel(intent: Intent) =
        intent.getStringExtra(EXTRA_TUN_NAME)?.let { tunnelsRepository.findByTunnelName(it) }

    companion object {
        const val EXTRA_TUN_NAME = "tunnelName"
        const val EXTRA_KEY = "key"
    }
}
