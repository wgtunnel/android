package com.zaneschepke.wireguardautotunnel.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
import androidx.core.app.NotificationCompat.Builder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import com.zaneschepke.wireguardautotunnel.MainActivity
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.broadcast.NotificationActionReceiver
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
import com.zaneschepke.wireguardautotunnel.notification.NotificationService.Companion.EXTRA_ID
import com.zaneschepke.wireguardautotunnel.util.StringValue

class AndroidNotificationService(override val context: Context) : NotificationService {

    private val notificationManager = NotificationManagerCompat.from(context)

    override fun createNotification(
        channel: NotificationChannels,
        title: String,
        subText: String?,
        actions: Collection<Action>,
        description: String,
        showTimestamp: Boolean,
        onGoing: Boolean,
        onlyAlertOnce: Boolean,
        groupKey: String?,
        isGroupSummary: Boolean,
        style: NotificationCompat.Style?,
    ): Notification {
        notificationManager.createNotificationChannel(channel.asChannel())
        return channel
            .asBuilder()
            .apply {
                actions.forEach { addAction(it) }
                setContentTitle(title)
                setSubText(subText)
                setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                setContentText(description)
                setOnlyAlertOnce(onlyAlertOnce)
                setOngoing(onGoing)
                setShowWhen(showTimestamp)
                setSmallIcon(R.drawable.ic_notification)
                if (groupKey != null) {
                    setGroup(groupKey)
                    if (isGroupSummary) {
                        setGroupSummary(true)
                    }
                }
                style?.let { setStyle(it) }
            }
            .build()
    }

    override fun createNotification(
        channel: NotificationChannels,
        title: StringValue,
        subText: String?,
        actions: Collection<Action>,
        description: StringValue,
        showTimestamp: Boolean,
        onGoing: Boolean,
        onlyAlertOnce: Boolean,
        groupKey: String?,
        isGroupSummary: Boolean,
        style: NotificationCompat.Style?,
    ): Notification {
        return createNotification(
            channel,
            title.asString(context),
            subText,
            actions,
            description.asString(context),
            showTimestamp,
            onGoing,
            onlyAlertOnce,
            groupKey,
            isGroupSummary,
            style,
        )
    }

    override fun createNotificationAction(
        notificationAction: NotificationAction,
        extraId: Int?,
    ): Action {
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                extraId ?: 0,
                Intent(context, NotificationActionReceiver::class.java).apply {
                    action = notificationAction.name
                    if (extraId != null) putExtra(EXTRA_ID, extraId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Action.Builder(
                R.drawable.ic_notification,
                notificationAction.title(context),
                pendingIntent,
            )
            .build()
    }

    override fun remove(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    override fun show(notificationId: Int, notification: Notification) {
        with(notificationManager) {
            if (
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notify(notificationId, notification)
        }
    }

    private fun NotificationChannels.asBuilder(): Builder {
        return when (this) {
            is NotificationChannels.Tunnel.VPN ->
                Builder(context, context.getString(R.string.vpn_channel_id))
            is NotificationChannels.Tunnel.Proxy ->
                Builder(context, context.getString(R.string.proxy_channel_id))
            NotificationChannels.AutoTunnel ->
                Builder(context, context.getString(R.string.auto_tunnel_channel_id))

            NotificationChannels.App -> Builder(context, context.getString(R.string.app_channel_id))
            NotificationChannels.Errors ->
                Builder(context, context.getString(R.string.errors_channel_id))
            NotificationChannels.Events ->
                Builder(context, context.getString(R.string.events_channel_id))
        }
    }

    sealed class NotificationChannels(val channelId: Int, val importance: Int) {
        sealed class Tunnel(channelId: Int, importance: Int) :
            NotificationChannels(channelId, importance) {

            data object VPN :
                Tunnel(channelId = R.string.vpn_channel_id, importance = IMPORTANCE_LOW)

            data object Proxy :
                Tunnel(channelId = R.string.proxy_channel_id, importance = IMPORTANCE_LOW)
        }

        data object AutoTunnel :
            NotificationChannels(
                channelId = R.string.auto_tunnel_channel_id,
                importance = IMPORTANCE_LOW,
            )

        data object Events :
            NotificationChannels(
                channelId = R.string.events_channel_id,
                importance = IMPORTANCE_LOW,
            )

        data object Errors :
            NotificationChannels(
                channelId = R.string.errors_channel_id,
                importance = IMPORTANCE_HIGH,
            )

        data object App :
            NotificationChannels(channelId = R.string.app_channel_id, importance = IMPORTANCE_LOW)

        companion object {
            val all: List<NotificationChannels> =
                listOf(Errors, Events, App, Tunnel.VPN, Tunnel.Proxy, AutoTunnel)
        }
    }

    fun NotificationChannels.asChannel(): NotificationChannel {
        val (nameResId, descriptionResId) =
            when (this) {
                is NotificationChannels.Tunnel.VPN ->
                    R.string.vpn to R.string.vpn_channel_description
                is NotificationChannels.Tunnel.Proxy ->
                    R.string.proxy to R.string.proxy_channel_description
                NotificationChannels.AutoTunnel ->
                    R.string.auto_tunnel to R.string.auto_tunnel_channel_description

                NotificationChannels.Errors ->
                    R.string.errors to R.string.errors_channel_description
                NotificationChannels.Events ->
                    R.string.events to R.string.events_channel_description
                NotificationChannels.App -> R.string.app to R.string.app_channel_description
            }

        return NotificationChannel(
                context.getString(channelId),
                context.getString(nameResId),
                importance,
            )
            .apply { description = context.getString(descriptionResId) }
    }

    override fun createAllChannels() {
        NotificationChannels.all.forEach { channel ->
            notificationManager.createNotificationChannel(channel.asChannel())
        }
    }
}
