package com.zaneschepke.wireguardautotunnel.core.notification

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
import com.zaneschepke.wireguardautotunnel.MainActivity
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.broadcast.NotificationActionReceiver
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.EXTRA_ID
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
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
        return NotificationCompat.Action.Builder(
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

    private fun NotificationChannels.asBuilder(): NotificationCompat.Builder {
        return when (this) {
            NotificationChannels.AUTO_TUNNEL ->
                Builder(context, context.getString(R.string.auto_tunnel_channel_id))
            NotificationChannels.VPN -> Builder(context, context.getString(R.string.vpn_channel_id))

            NotificationChannels.PROXY ->
                Builder(context, context.getString(R.string.proxy_channel_id))
        }
    }

    enum class NotificationChannels(val channelId: Int, val importance: Int) {
        VPN(R.string.vpn_channel_id, IMPORTANCE_LOW),
        AUTO_TUNNEL(R.string.auto_tunnel_channel_id, IMPORTANCE_LOW),
        PROXY(R.string.proxy_channel_id, IMPORTANCE_LOW),
    }

    fun NotificationChannels.asChannel(): NotificationChannel {
        return NotificationChannel(
                context.getString(channelId),
                context.getString(
                    when (this) {
                        NotificationChannels.VPN -> R.string.vpn
                        NotificationChannels.AUTO_TUNNEL -> R.string.auto_tunnel
                        NotificationChannels.PROXY -> R.string.proxy
                    }
                ),
                importance,
            )
            .apply {
                description =
                    context.getString(
                        when (this@asChannel) {
                            NotificationChannels.VPN -> R.string.vpn_channel_description
                            NotificationChannels.AUTO_TUNNEL ->
                                R.string.auto_tunnel_channel_description
                            NotificationChannels.PROXY -> R.string.proxy_channel_description
                        }
                    )
            }
    }

    override fun createAllChannels() {
        NotificationChannels.entries.forEach { channel ->
            notificationManager.createNotificationChannel(channel.asChannel())
        }
    }
}
