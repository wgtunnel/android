package com.zaneschepke.wireguardautotunnel.core.notification

import com.zaneschepke.tunnel.state.ActiveTunnel

interface TunnelNotificationService {

    suspend fun updatePersistentNotifications(activeTunnels: Map<Int, ActiveTunnel>)

    suspend fun showIpv4Fallback(tunnelId: Int)

    suspend fun showIpv6Recovery(tunnelId: Int)

    suspend fun showDynamicDnsUpdate(tunnelId: Int)

    suspend fun showVpnRequired()

    suspend fun showStateConflict(tunnelId: Int)

    suspend fun showRootShellAccess()

    suspend fun showError(message: String)
}
