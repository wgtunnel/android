package com.zaneschepke.wireguardautotunnel.notification

import android.text.format.Formatter
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource
import com.zaneschepke.wireguardautotunnel.notification.AndroidNotificationService.NotificationChannels
import com.zaneschepke.wireguardautotunnel.notification.NotificationService.Companion.TUNNEL_ERROR_NOTIFICATION_ID
import com.zaneschepke.wireguardautotunnel.notification.NotificationService.Companion.TUNNEL_MESSAGES_NOTIFICATION_ID
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState

class AndroidTunnelNotificationService(private val notificationService: NotificationService) :
    TunnelNotificationService {

    private val context = notificationService.context

    private fun createGroupNotification(
        tunnelNotificationLines: Map<Int, TunnelNotificationLine>,
        channel: NotificationChannels.Tunnel,
        options: TunnelNotificationOptions,
        lockdown: Boolean = false,
    ): android.app.Notification {
        val singleTunnel = tunnelNotificationLines.size == 1
        val promote =
            options.liveUpdatesEnabled &&
                NotificationManagerCompat.from(context).canPostPromotedNotifications()

        val kind =
            when {
                lockdown -> context.getString(R.string.lockdown)
                channel is NotificationChannels.Tunnel.VPN -> context.getString(R.string.vpn)
                channel is NotificationChannels.Tunnel.Proxy -> context.getString(R.string.proxy)
                else -> context.getString(R.string.vpn)
            }

        val title =
            when {
                singleTunnel -> "$kind • ${tunnelNotificationLines.values.first().name}"
                else -> kind
            }

        val formattedLines =
            tunnelNotificationLines.values.map { line ->
                formatLine(line, includeName = !singleTunnel, options = options)
            }

        val description = formattedLines.joinToString("\n")

        val actions =
            when {
                singleTunnel ->
                    listOf(
                        notificationService.createNotificationAction(
                            notificationAction = NotificationAction.TUNNEL_OFF,
                            extraId = tunnelNotificationLines.keys.first(),
                            authenticationRequired = true,
                        )
                    )
                tunnelNotificationLines.isNotEmpty() ->
                    listOf(
                        notificationService.createNotificationAction(
                            notificationAction = NotificationAction.STOP_ALL,
                            extraId = null,
                            authenticationRequired = true,
                        )
                    )
                else -> emptyList()
            }

        val style =
            when {
                tunnelNotificationLines.size > 1 ->
                    NotificationCompat.InboxStyle()
                        .setBigContentTitle(title)
                        .setSummaryText(
                            "${tunnelNotificationLines.size} ${context.getString(R.string.tunnels).lowercase()}"
                        )
                        .also { inbox -> formattedLines.forEach { inbox.addLine(it) } }
                description.contains('\n') -> NotificationCompat.BigTextStyle().bigText(description)
                else -> null
            }

        val shortCriticalText = if (promote) kind else null

        val chronometerBaseMillis =
            if (options.liveUpdatesEnabled && singleTunnel) {
                tunnelNotificationLines.values.first().startedAtMillis
            } else {
                null
            }

        val color =
            if (
                options.showFailureTint &&
                    tunnelNotificationLines.values.any {
                        it.displayState == DisplayTunnelState.HandshakeFailure
                    }
            ) {
                DisplayTunnelState.HandshakeFailure.asColor().toArgb()
            } else {
                null
            }

        return notificationService.createNotification(
            channel = channel,
            title = title,
            description = description,
            actions = actions,
            onGoing = true,
            onlyAlertOnce = true,
            showTimestamp = chronometerBaseMillis != null,
            style = style,
            requestPromotedOngoing = promote,
            shortCriticalText = shortCriticalText,
            chronometerBaseMillis = chronometerBaseMillis,
            color = color,
        )
    }

    private fun formatLine(
        line: TunnelNotificationLine,
        includeName: Boolean,
        options: TunnelNotificationOptions,
    ): String {
        val parts = labeledParts(line, options)
        return if (includeName) {
            (listOf(line.name) + parts).joinToString(" • ")
        } else {
            parts.joinToString("\n")
        }
    }

    private fun labeledParts(
        line: TunnelNotificationLine,
        options: TunnelNotificationOptions,
    ): List<String> {
        val parts = mutableListOf<String>()
        parts +=
            context.getString(
                R.string.notification_status_format,
                line.displayState.asLocalizedString(context),
            )
        if (options.showOrigin) {
            when (line.origin) {
                TunnelActionSource.USER ->
                    parts +=
                        context.getString(
                            R.string.notification_source_format,
                            context.getString(R.string.notification_connection_manual),
                        )
                TunnelActionSource.AUTO_TUNNEL ->
                    parts +=
                        context.getString(
                            R.string.notification_source_format,
                            context.getString(R.string.notification_connection_auto),
                        )
                null -> Unit
            }
        }
        if (options.showTransfer) {
            parts +=
                context.getString(
                    R.string.notification_transfer_format,
                    Formatter.formatFileSize(context, line.rxBytes),
                    Formatter.formatFileSize(context, line.txBytes),
                )
        }
        if (options.showRecovery && line.recoveryAttempts > 0) {
            parts += context.getString(R.string.notification_recovery_format, line.recoveryAttempts)
        }
        return parts
    }

    override fun buildVpnPersistentNotification(
        tunnelNotificationLines: Map<Int, TunnelNotificationLine>,
        options: TunnelNotificationOptions,
        lockdown: Boolean,
    ): android.app.Notification {
        return createGroupNotification(
            tunnelNotificationLines,
            NotificationChannels.Tunnel.VPN,
            options,
            lockdown = lockdown,
        )
    }

    override fun buildProxyPersistentNotification(
        tunnelNotificationLines: Map<Int, TunnelNotificationLine>,
        options: TunnelNotificationOptions,
    ): android.app.Notification {
        return createGroupNotification(
            tunnelNotificationLines,
            NotificationChannels.Tunnel.Proxy,
            options,
        )
    }

    override fun showIpv4Fallback(tunnelName: String) {
        showEvent(
            title = "${context.getString(R.string.ipv4_fallback)} • $tunnelName",
            message = context.getString(R.string.notification_ipv4_fallback_message, tunnelName),
        )
    }

    override fun showIpv6Recovery(tunnelName: String) {
        showEvent(
            title = "${context.getString(R.string.ipv6_recovery)} • $tunnelName",
            message = context.getString(R.string.notification_ipv6_recovery_message, tunnelName),
        )
    }

    override fun showDynamicDnsUpdate(tunnelName: String) {
        showEvent(
            title = "${context.getString(R.string.dynamic_dns_update)} • $tunnelName",
            message = context.getString(R.string.notification_dynamic_dns_message, tunnelName),
        )
    }

    override fun showSeamlessRecoveryAttempt(tunnelName: String) {
        showEvent(
            title = "${context.getString(R.string.seamless_recovery)} • $tunnelName",
            message = context.getString(R.string.seamless_recovery_attempt_message),
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
        showErrorNotification(
            title = "${context.getString(R.string.error)} • $tunnelName",
            message = context.getString(R.string.error_socks5_port_unavailable, port),
        )
    }

    override fun showHttpPortUnavailable(port: Int, tunnelName: String) {
        showErrorNotification(
            title = "${context.getString(R.string.error)} • $tunnelName",
            message = context.getString(R.string.error_http_port_unavailable, port),
        )
    }

    override fun showConfigMissingDns(tunnelName: String) {
        val context = notificationService.context
        val message = context.getString(R.string.error_config_missing_dns, tunnelName)
        showError(message)
    }

    override fun showError(message: String) {
        showErrorNotification(title = context.getString(R.string.error), message = message)
    }

    private fun showErrorNotification(title: String, message: String) {
        val notification =
            notificationService.createNotification(
                channel = NotificationChannels.Errors,
                title = title,
                description = message,
                onGoing = false,
                onlyAlertOnce = true,
                style = NotificationCompat.BigTextStyle().bigText(message),
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
            )

        notificationService.show(TUNNEL_MESSAGES_NOTIFICATION_ID, notification)
    }
}
