package com.zaneschepke.wireguardautotunnel.core.notification

import androidx.core.app.NotificationCompat
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.notification.AndroidNotificationService.NotificationChannels
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.PROXY_GROUP_KEY
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.PROXY_NOTIFICATION_ID
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.TUNNEL_ERROR_NOTIFICATION_ID
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.TUNNEL_MESSAGES_NOTIFICATION_ID
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.VPN_GROUP_KEY
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.VPN_NOTIFICATION_ID
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction

class AndroidTunnelNotificationService(private val notificationService: NotificationService) :
    TunnelNotificationService {

    private val context = notificationService.context

    private fun updateGroupNotification(
        tunnelNotificationLines: Map<Int, TunnelNotificationLine>,
        notificationId: Int,
        channel: NotificationChannels.Tunnel,
        groupKey: String,
    ) {
        if (tunnelNotificationLines.isEmpty()) {
            notificationService.remove(notificationId)
            return
        }

        val context = notificationService.context

        val formattedLines =
            tunnelNotificationLines.values.map { line ->
                val status = line.displayState.asLocalizedString(context)
                context.getString(R.string.notification_tunnel_status_format, line.name, status)
            }

        val description = formattedLines.joinToString("\n")

        val actions =
            if (tunnelNotificationLines.size == 1) {
                val tunnelId = tunnelNotificationLines.keys.first()
                listOf(
                    notificationService.createNotificationAction(
                        notificationAction = NotificationAction.TUNNEL_OFF,
                        extraId = tunnelId,
                    )
                )
            } else {
                listOf(
                    notificationService.createNotificationAction(
                        notificationAction = NotificationAction.STOP_ALL,
                        extraId = null,
                    )
                )
            }

        // Show the tunnel name as the title when a single tunnel is active so it remains
        // visible in the collapsed notification; fall back to the generic channel name otherwise.
        val title =
            if (tunnelNotificationLines.size == 1) {
                tunnelNotificationLines.values.first().name
            } else {
                when (channel) {
                    is NotificationChannels.Tunnel.VPN -> context.getString(R.string.vpn)
                    is NotificationChannels.Tunnel.Proxy -> context.getString(R.string.proxy)
                }
            }

        val style =
            if (tunnelNotificationLines.size > 1) {
                NotificationCompat.InboxStyle()
                    .setBigContentTitle(title)
                    .setSummaryText(
                        "${tunnelNotificationLines.size} ${context.getString(R.string.tunnels).lowercase()}"
                    )
                    .also { inboxStyle -> formattedLines.forEach { inboxStyle.addLine(it) } }
            } else {
                null
            }

        val notification =
            notificationService.createNotification(
                channel = channel,
                title = title,
                description = description,
                actions = actions,
                onGoing = true,
                onlyAlertOnce = true,
                groupKey = groupKey,
                style = style,
            )

        notificationService.show(notificationId, notification)
    }

    override fun updateProxyPersistentNotification(
        tunnelNotificationLines: Map<Int, TunnelNotificationLine>
    ) {
        updateGroupNotification(
            tunnelNotificationLines = tunnelNotificationLines,
            notificationId = PROXY_NOTIFICATION_ID,
            channel = NotificationChannels.Tunnel.Proxy,
            groupKey = PROXY_GROUP_KEY,
        )
    }

    override fun updateVpnPersistentNotification(
        tunnelNotificationLines: Map<Int, TunnelNotificationLine>
    ) {
        updateGroupNotification(
            tunnelNotificationLines = tunnelNotificationLines,
            notificationId = VPN_NOTIFICATION_ID,
            channel = NotificationChannels.Tunnel.VPN,
            groupKey = VPN_GROUP_KEY,
        )
    }

    override fun showIpv4Fallback(tunnelName: String) {
        showEvent(
            title = context.getString(R.string.ipv4_fallback),
            message = context.getString(R.string.notification_ipv4_fallback_message, tunnelName),
        )
    }

    override fun showIpv6Recovery(tunnelName: String) {
        showEvent(
            title = context.getString(R.string.ipv6_recovery),
            message = context.getString(R.string.notification_ipv6_recovery_message, tunnelName),
        )
    }

    override fun showDynamicDnsUpdate(tunnelName: String) {
        showEvent(
            title = context.getString(R.string.dynamic_dns_update),
            message = context.getString(R.string.notification_dynamic_dns_message, tunnelName),
        )
    }

    override fun showVpnRequired() {
        showError(notificationService.context.getString(R.string.vpn_permission_required))
    }

    override fun showRootShellAccess() {
        // TODO could improve with fix action
        val context = notificationService.context
        showError(context.getString(R.string.error_root_denied))
    }

    override fun showSocks5PortUnavailable(port: Int, tunnelName: String) {
        val context = notificationService.context
        val message = context.getString(R.string.error_socks5_port_unavailable, port)

        showError(message)
    }

    override fun showHttpPortUnavailable(port: Int, tunnelName: String) {
        val context = notificationService.context
        val message = context.getString(R.string.error_http_port_unavailable, port)

        showError(message)
    }

    override fun showError(message: String) {
        val notification =
            notificationService.createNotification(
                channel = NotificationChannels.Errors,
                title = notificationService.context.getString(R.string.error),
                description = message,
                onGoing = false,
                onlyAlertOnce = true,
                groupKey = VPN_GROUP_KEY,
            )

        notificationService.show(TUNNEL_ERROR_NOTIFICATION_ID, notification)
    }

    private fun showEvent(title: String, message: String) {

        val notification =
            notificationService.createNotification(
                channel = NotificationChannels.Events,
                title = title,
                description = message,
                onGoing = false,
                onlyAlertOnce = true,
                groupKey = VPN_GROUP_KEY,
            )

        notificationService.show(TUNNEL_MESSAGES_NOTIFICATION_ID, notification)
    }
}
