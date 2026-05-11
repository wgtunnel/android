package com.zaneschepke.wireguardautotunnel.core.notification

import com.zaneschepke.tunnel.state.BackendStatus

interface TunnelNotificationService {

    suspend fun updatePersistentNotifications(status: BackendStatus)

    suspend fun showIpv4Fallback(tunnelId: Int)

    suspend fun showIpv6Recovery(tunnelId: Int)

    suspend fun showDynamicDnsUpdate(tunnelId: Int)

    suspend fun showVpnRequired()

    suspend fun showStateConflict(tunnelId: Int)

    suspend fun showError(message: String)
}
