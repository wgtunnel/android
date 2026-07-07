package com.zaneschepke.wireguardautotunnel.notification

import android.app.Notification

interface TunnelNotificationService {

    fun updateProxyPersistentNotification(tunnelNotificationLines: Map<Int, TunnelNotificationLine>)

    fun updateVpnPersistentNotification(tunnelNotificationLines: Map<Int, TunnelNotificationLine>)

    fun buildVpnPersistentNotification(
        tunnelNotificationLines: Map<Int, TunnelNotificationLine>
    ): Notification

    fun buildProxyPersistentNotification(
        tunnelNotificationLines: Map<Int, TunnelNotificationLine>
    ): Notification

    fun showIpv4Fallback(tunnelName: String)

    fun showIpv6Recovery(tunnelName: String)

    fun showDynamicDnsUpdate(tunnelName: String)

    fun showVpnRequired()

    fun showSocks5PortUnavailable(port: Int, tunnelName: String)

    fun showHttpPortUnavailable(port: Int, tunnelName: String)

    fun showRootShellAccess()

    fun showError(message: String)
}
