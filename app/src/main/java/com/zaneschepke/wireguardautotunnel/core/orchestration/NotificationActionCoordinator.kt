package com.zaneschepke.wireguardautotunnel.core.orchestration

import android.content.Intent
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
import com.zaneschepke.wireguardautotunnel.notification.NotificationService

class NotificationActionCoordinator(
    private val tunnelCoordinator: TunnelCoordinator,
    private val autoTunnelCoordinator: AutoTunnelCoordinator,
) {

    suspend fun handle(intent: Intent) {
        when (intent.action) {
            NotificationAction.AUTO_TUNNEL_OFF.name -> {
                autoTunnelCoordinator.disable()
            }

            NotificationAction.TUNNEL_OFF.name -> {

                val tunnelId = intent.getIntExtra(NotificationService.EXTRA_ID, STOP_ALL_TUNNELS_ID)

                if (tunnelId == STOP_ALL_TUNNELS_ID) {
                    tunnelCoordinator.stopActiveTunnels()
                    return
                }
                tunnelCoordinator.stopTunnel(tunnelId)
            }

            NotificationAction.STOP_ALL.name -> {
                tunnelCoordinator.stopActiveTunnels()
            }
        }
    }

    companion object {
        const val STOP_ALL_TUNNELS_ID = 0
    }
}
