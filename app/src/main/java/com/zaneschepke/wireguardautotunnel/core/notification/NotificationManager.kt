package com.zaneschepke.wireguardautotunnel.core.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.zaneschepke.wireguardautotunnel.core.notification.WireGuardNotification.NotificationChannels
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
import com.zaneschepke.wireguardautotunnel.util.StringValue

interface NotificationManager {
    val context: Context

    fun createNotification(
        channel: NotificationChannels,
        title: String = "",
        subText: String? = null,
        actions: Collection<NotificationCompat.Action> = emptyList(),
        description: String = "",
        showTimestamp: Boolean = false,
        importance: Int = NotificationManager.IMPORTANCE_LOW,
        onGoing: Boolean = false,
        onlyAlertOnce: Boolean = true,
        groupKey: String? = null,
        isGroupSummary: Boolean = false,
    ): Notification

    fun createNotification(
        channel: NotificationChannels,
        title: StringValue,
        subText: String? = null,
        actions: Collection<NotificationCompat.Action> = emptyList(),
        description: StringValue,
        showTimestamp: Boolean = true,
        importance: Int = NotificationManager.IMPORTANCE_LOW,
        onGoing: Boolean = false,
        onlyAlertOnce: Boolean = true,
        groupKey: String? = null,
        isGroupSummary: Boolean = false,
    ): Notification

    fun createNotificationAction(
        notificationAction: NotificationAction,
        extraId: Int? = null,
    ): NotificationCompat.Action

    fun remove(notificationId: Int)

    fun show(notificationId: Int, notification: Notification)

    companion object {
        const val VPN_GROUP_KEY = "VPN_GROUP"
        const val AUTO_TUNNEL_GROUP_KEY = "AUTO_TUNNEL_GROUP"
        const val AUTO_TUNNEL_LOCATION_PERMISSION_ID = 123
        const val AUTO_TUNNEL_LOCATION_SERVICES_ID = 124
        // For auto tunnel foreground notification
        const val AUTO_TUNNEL_NOTIFICATION_ID = 122
        // for tunnel foreground notification
        const val VPN_NOTIFICATION_ID = 100
        const val TUNNEL_ERROR_NOTIFICATION_ID = 101
        const val TUNNEL_MESSAGES_NOTIFICATION_ID = 102
        const val EXTRA_ID = "id"
    }
}
