package com.zaneschepke.wireguardautotunnel.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationManager
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.di.Scope
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val tunnelManager: TunnelManager by inject()
    private val autoTunnelRepository: AutoTunnelSettingsRepository by inject()
    private val applicationScope: CoroutineScope = get(named(Scope.APPLICATION))

    override fun onReceive(context: Context, intent: Intent) {
        applicationScope.launch {
            when (intent.action) {
                NotificationAction.AUTO_TUNNEL_OFF.name ->
                    autoTunnelRepository.updateAutoTunnelEnabled(false)
                NotificationAction.TUNNEL_OFF.name -> {
                    val tunnelId = intent.getIntExtra(NotificationManager.EXTRA_ID, 0)
                    if (tunnelId == STOP_ALL_TUNNELS_ID)
                        return@launch tunnelManager.stopActiveTunnels()
                    tunnelManager.stopTunnel(tunnelId)
                }
            }
        }
    }

    companion object {
        const val STOP_ALL_TUNNELS_ID = 0
    }
}
