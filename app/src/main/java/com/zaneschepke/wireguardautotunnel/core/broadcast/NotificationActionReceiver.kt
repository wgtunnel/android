package com.zaneschepke.wireguardautotunnel.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zaneschepke.wireguardautotunnel.core.orchestration.AutoTunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.di.Scope
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
import com.zaneschepke.wireguardautotunnel.notification.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val tunnelCoordinator: TunnelCoordinator by inject()

    private val autoTunnelCoordinator: AutoTunnelCoordinator by inject()

    private val applicationScope: CoroutineScope = get(named(Scope.APPLICATION))

    override fun onReceive(context: Context, intent: Intent) {

        applicationScope.launch {
            when (intent.action) {
                NotificationAction.AUTO_TUNNEL_OFF.name -> {
                    autoTunnelCoordinator.disable()
                }

                NotificationAction.TUNNEL_OFF.name -> {

                    val tunnelId =
                        intent.getIntExtra(NotificationService.EXTRA_ID, STOP_ALL_TUNNELS_ID)

                    if (tunnelId == STOP_ALL_TUNNELS_ID) {
                        tunnelCoordinator.stopActiveTunnels()
                        return@launch
                    }
                    tunnelCoordinator.stopTunnel(tunnelId)
                }

                NotificationAction.STOP_ALL.name -> {
                    tunnelCoordinator.stopActiveTunnels()
                }
            }
        }
    }

    companion object {
        const val STOP_ALL_TUNNELS_ID = 0
    }
}
