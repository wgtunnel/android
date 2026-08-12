package com.zaneschepke.wireguardautotunnel.core.tunnel

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wgtunnel.backend.AndroidApplicationProvider
import com.wgtunnel.backend.model.BackendMode
import com.wgtunnel.backend.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.MainActivity
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.notification.AndroidNotificationService
import com.zaneschepke.wireguardautotunnel.notification.NotificationService
import com.zaneschepke.wireguardautotunnel.notification.NotificationService.Companion.PROXY_GROUP_KEY
import com.zaneschepke.wireguardautotunnel.notification.NotificationService.Companion.VPN_GROUP_KEY
import com.zaneschepke.wireguardautotunnel.notification.TunnelNotificationLine
import com.zaneschepke.wireguardautotunnel.notification.TunnelNotificationService
import com.zaneschepke.wireguardautotunnel.service.tile.TunnelTileRefresher
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState
import kotlinx.coroutines.flow.first

class AppProvider(
    private val notificationService: NotificationService,
    private val tunnelNotificationService: TunnelNotificationService,
    private val tunnelRepository: TunnelRepository,
) : AndroidApplicationProvider {

    override val context: Context = notificationService.context

    override fun refreshStatusUi() {
        TunnelTileRefresher.refresh(context)
    }

    override fun createVpnConfigurePendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override val vpnInitNotification: Notification
        get() =
            notificationService.createNotification(
                channel = AndroidNotificationService.NotificationChannels.Tunnel.VPN,
                title = context.getString(R.string.initializing),
                onGoing = true,
                groupKey = VPN_GROUP_KEY,
            )

    override val proxyInitNotification: Notification
        get() =
            notificationService.createNotification(
                channel = AndroidNotificationService.NotificationChannels.Tunnel.Proxy,
                title = context.getString(R.string.initializing),
                onGoing = true,
                groupKey = PROXY_GROUP_KEY,
            )

    override val vpnNotificationId: Int
        get() = NotificationService.VPN_NOTIFICATION_ID

    override val proxyNotificationId: Int
        get() = NotificationService.PROXY_NOTIFICATION_ID

    override suspend fun buildVpnPersistentNotification(status: BackendStatus): Notification {
        val lines = computeVpnNotificationLines(status)
        return tunnelNotificationService.buildVpnPersistentNotification(lines)
    }

    override suspend fun buildProxyPersistentNotification(status: BackendStatus): Notification {
        val lines = computeProxyNotificationLines(status)
        return tunnelNotificationService.buildProxyPersistentNotification(lines)
    }

    private suspend fun computeVpnNotificationLines(
        status: BackendStatus
    ): Map<Int, TunnelNotificationLine> {
        val activeTunnels = status.activeTunnels
        val allTunnels = tunnelRepository.userTunnelsFlow.first()
        return activeTunnels
            .mapNotNull { (id, activeTunnel) ->
                val mode = activeTunnel.mode ?: return@mapNotNull null
                if (mode !is BackendMode.Vpn && mode !is BackendMode.Proxy.KillSwitchPrimary)
                    return@mapNotNull null
                val tunnel = allTunnels.find { it.id == id } ?: return@mapNotNull null
                val displayState = DisplayTunnelState.from(activeTunnel)
                TunnelNotificationLine(id, tunnel.name, displayState)
            }
            .associateBy { it.id }
    }

    private suspend fun computeProxyNotificationLines(
        status: BackendStatus
    ): Map<Int, TunnelNotificationLine> {
        val activeTunnels = status.activeTunnels
        val allTunnels = tunnelRepository.userTunnelsFlow.first()
        return activeTunnels
            .mapNotNull { (id, activeTunnel) ->
                val mode = activeTunnel.mode ?: return@mapNotNull null
                if (mode !is BackendMode.Proxy.Standard) return@mapNotNull null
                val tunnel = allTunnels.find { it.id == id } ?: return@mapNotNull null
                val displayState = DisplayTunnelState.from(activeTunnel)
                TunnelNotificationLine(id, tunnel.name, displayState)
            }
            .associateBy { it.id }
    }
}
