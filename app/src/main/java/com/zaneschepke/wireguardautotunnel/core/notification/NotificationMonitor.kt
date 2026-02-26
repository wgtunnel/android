package com.zaneschepke.wireguardautotunnel.core.notification

import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.WireGuardAutoTunnel
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.domain.events.BackendMessage
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.util.StringValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationMonitor(
    private val tunnelManager: TunnelManager,
    private val notificationManager: NotificationManager,
    private val monitoringSettingsRepository: MonitoringSettingsRepository,
) {
    suspend fun handleApplicationNotifications() = coroutineScope {
        launch { handleTunnelErrors() }
        launch { handleTunnelMessages() }
        launch { handleRecoveryNotifications() }
    }

    private suspend fun handleTunnelErrors() =
        tunnelManager.errorEvents.collectLatest { (tunName, error) ->
            if (!WireGuardAutoTunnel.uiActive.value) {
                val notification =
                    notificationManager.createNotification(
                        WireGuardNotification.NotificationChannels.VPN,
                        title =
                            tunName?.let { StringValue.DynamicString(it) }
                                ?: StringValue.StringResource(R.string.tunnel),
                        description =
                            StringValue.StringResource(
                                R.string.tunnel_error_template,
                                error.stringRes,
                            ),
                        groupKey = NotificationManager.VPN_GROUP_KEY,
                    )
                notificationManager.show(
                    NotificationManager.TUNNEL_ERROR_NOTIFICATION_ID,
                    notification,
                )
            }
        }

    private suspend fun handleTunnelMessages() =
        tunnelManager.messageEvents.collectLatest { (tunName, message) ->
            val stringValue = message.toStringValue() ?: return@collectLatest
            if (!WireGuardAutoTunnel.uiActive.value) {
                val notification =
                    notificationManager.createNotification(
                        WireGuardNotification.NotificationChannels.VPN,
                        title =
                            tunName?.let { StringValue.DynamicString(it) }
                                ?: StringValue.StringResource(R.string.tunnel),
                        description = stringValue,
                        groupKey = NotificationManager.VPN_GROUP_KEY,
                    )
                notificationManager.show(
                    NotificationManager.TUNNEL_MESSAGES_NOTIFICATION_ID,
                    notification,
                )
            }
        }

    private suspend fun handleRecoveryNotifications() {
        tunnelManager.messageEvents.collect { (tunName, message) ->
            val notificationsEnabled =
                monitoringSettingsRepository.getMonitoringSettings().isRecoveryNotificationEnabled
            val title =
                tunName?.let { StringValue.DynamicString(it) }
                    ?: StringValue.StringResource(R.string.tunnel)
            when (message) {
                is BackendMessage.ConnectionDegrading -> {
                    if (notificationsEnabled) {
                        val reasonRes =
                            if (message.reason == BackendMessage.RestartReason.STALE_HANDSHAKE)
                                R.string.restart_reason_stale_handshake
                            else R.string.restart_reason_ping_failure
                        val notification =
                            notificationManager.createNotification(
                                WireGuardNotification.NotificationChannels.VPN,
                                title = title,
                                description =
                                    StringValue.StringResource(
                                        R.string.notif_recovery_degraded,
                                        reasonRes,
                                        message.attempt.toString(),
                                        message.maxAttempts.toString(),
                                    ),
                                groupKey = NotificationManager.VPN_GROUP_KEY,
                                onGoing = true,
                            )
                        notificationManager.show(NotificationManager.RECOVERY_NOTIFICATION_ID, notification)
                    }
                }
                is BackendMessage.ConnectionRestored -> {
                    notificationManager.remove(NotificationManager.RECOVERY_NOTIFICATION_ID)
                    if (notificationsEnabled) {
                        val notification =
                            notificationManager.createNotification(
                                WireGuardNotification.NotificationChannels.VPN,
                                title = title,
                                description = StringValue.StringResource(R.string.notif_recovery_restored),
                                groupKey = NotificationManager.VPN_GROUP_KEY,
                            )
                        notificationManager.show(NotificationManager.RECOVERY_NOTIFICATION_ID, notification)
                    }
                }
                is BackendMessage.ConnectionPermanentlyLost -> {
                    notificationManager.remove(NotificationManager.RECOVERY_NOTIFICATION_ID)
                    if (notificationsEnabled) {
                        val reasonRes =
                            if (message.reason == BackendMessage.RestartReason.STALE_HANDSHAKE)
                                R.string.restart_reason_stale_handshake
                            else R.string.restart_reason_ping_failure
                        val descRes =
                            if (message.isTunnelStopped)
                                StringValue.StringResource(R.string.notif_recovery_failed_stopped, reasonRes)
                            else
                                StringValue.StringResource(R.string.notif_recovery_failed, reasonRes)
                        val notification =
                            notificationManager.createNotification(
                                WireGuardNotification.NotificationChannels.VPN,
                                title = title,
                                description = descRes,
                                groupKey = NotificationManager.VPN_GROUP_KEY,
                            )
                        notificationManager.show(NotificationManager.RECOVERY_NOTIFICATION_ID, notification)
                    }
                }
                is BackendMessage.ConnectionCancelled -> {
                    notificationManager.remove(NotificationManager.RECOVERY_NOTIFICATION_ID)
                }
                else -> {}
            }
        }
    }
}
