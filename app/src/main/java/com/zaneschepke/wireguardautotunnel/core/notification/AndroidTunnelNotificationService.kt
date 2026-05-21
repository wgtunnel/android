package com.zaneschepke.wireguardautotunnel.core.notification

import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.notification.AndroidNotificationService.NotificationChannels
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.PROXY_GROUP_KEY
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.PROXY_NOTIFICATION_ID
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.TUNNEL_ERROR_NOTIFICATION_ID
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.TUNNEL_MESSAGES_NOTIFICATION_ID
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.VPN_GROUP_KEY
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.VPN_NOTIFICATION_ID
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState

class AndroidTunnelNotificationService(
    private val notificationService: NotificationService,
    private val tunnelRepository: TunnelRepository,
) : TunnelNotificationService {

    override suspend fun updatePersistentNotifications(activeTunnels: Map<Int, ActiveTunnel>) {

        val vpnTunnels = activeTunnels.filterValues { it.mode is BackendMode.Vpn }

        val proxyTunnels = activeTunnels.filterValues { it.mode is BackendMode.Proxy }

        updateGroupNotification(
            tunnels = vpnTunnels,
            notificationId = VPN_NOTIFICATION_ID,
            channel = NotificationChannels.VPN,
            groupKey = VPN_GROUP_KEY,
        )

        updateGroupNotification(
            tunnels = proxyTunnels,
            notificationId = PROXY_NOTIFICATION_ID,
            channel = NotificationChannels.PROXY,
            groupKey = PROXY_GROUP_KEY,
        )
    }

    private suspend fun updateGroupNotification(
        tunnels: Map<Int, ActiveTunnel>,
        notificationId: Int,
        channel: NotificationChannels,
        groupKey: String,
    ) {

        if (tunnels.isEmpty()) {
            notificationService.remove(notificationId)
            return
        }

        val context = notificationService.context

        val lines = tunnels.mapNotNull { (id, activeTunnel) ->
            val tunnel = tunnelRepository.getById(id) ?: return@mapNotNull null
            val display = DisplayTunnelState.from(activeTunnel)

            context.getString(
                R.string.notification_tunnel_status_format,
                tunnel.name,
                display.asLocalizedString(context),
            )
        }

        val description = lines.joinToString("\n")

        val stopActions =
            tunnels.keys.map {
                notificationService.createNotificationAction(
                    notificationAction = NotificationAction.TUNNEL_OFF,
                    extraId = it,
                )
            }

        val title =
            when (channel) {
                NotificationChannels.VPN -> context.getString(R.string.vpn)

                NotificationChannels.PROXY -> context.getString(R.string.proxy)

                NotificationChannels.AUTO_TUNNEL -> context.getString(R.string.auto_tunnel)
            }

        val notification =
            notificationService.createNotification(
                channel = channel,
                title = title,
                description = description,
                actions = stopActions,
                onGoing = true,
                onlyAlertOnce = true,
                groupKey = groupKey,
            )

        notificationService.show(notificationId, notification)
    }

    override suspend fun showIpv4Fallback(tunnelId: Int) {

        val context = notificationService.context
        val name = tunnelName(tunnelId)

        showMessage(
            title = context.getString(R.string.ipv4_fallback),
            message = context.getString(R.string.notification_ipv4_fallback_message, name),
        )
    }

    override suspend fun showIpv6Recovery(tunnelId: Int) {

        val context = notificationService.context
        val name = tunnelName(tunnelId)

        showMessage(
            title = context.getString(R.string.ipv6_recovery),
            message = context.getString(R.string.notification_ipv6_recovery_message, name),
        )
    }

    override suspend fun showDynamicDnsUpdate(tunnelId: Int) {

        val context = notificationService.context
        val name = tunnelName(tunnelId)

        showMessage(
            title = context.getString(R.string.dynamic_dns_update),
            message = context.getString(R.string.notification_dynamic_dns_message, name),
        )
    }

    override suspend fun showVpnRequired() {

        showError(notificationService.context.getString(R.string.vpn_permission_required))
    }

    override suspend fun showStateConflict(tunnelId: Int) {

        val context = notificationService.context
        val name = tunnelName(tunnelId)

        showError(context.getString(R.string.notification_tunnel_already_running, name))
    }

    override suspend fun showRootShellAccess() {
        // TODO could improve with fix action
        val context = notificationService.context
        showError(context.getString(R.string.error_root_denied))
    }

    override suspend fun showError(message: String) {

        val notification =
            notificationService.createNotification(
                channel = NotificationChannels.VPN,
                title = notificationService.context.getString(R.string.error),
                description = message,
                onGoing = false,
                onlyAlertOnce = true,
                groupKey = VPN_GROUP_KEY,
            )

        notificationService.show(TUNNEL_ERROR_NOTIFICATION_ID, notification)
    }

    private fun showMessage(title: String, message: String) {

        val notification =
            notificationService.createNotification(
                channel = NotificationChannels.VPN,
                title = title,
                description = message,
                onGoing = false,
                onlyAlertOnce = true,
                groupKey = VPN_GROUP_KEY,
            )

        notificationService.show(TUNNEL_MESSAGES_NOTIFICATION_ID, notification)
    }

    private suspend fun tunnelName(id: Int): String {

        val context = notificationService.context

        return tunnelRepository.getById(id)?.name ?: context.getString(R.string.unknown, id)
    }
}
