package com.zaneschepke.tunnel

import android.app.Notification

interface NotificationProvider {
    val vpnInitNotification: Notification
    val proxyInitNotification: Notification
    val vpnNotificationId: Int
    val proxyNotificationId: Int
}
