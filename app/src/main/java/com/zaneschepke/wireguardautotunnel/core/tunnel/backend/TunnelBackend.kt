package com.zaneschepke.wireguardautotunnel.core.tunnel.backend

import com.zaneschepke.wireguardautotunnel.domain.enums.BackendMode
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelStatus
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelStatistics
import kotlinx.coroutines.flow.Flow

interface TunnelBackend {
    fun tunnelStateFlow(tunnelConfig: TunnelConfig): Flow<TunnelStatus>

    fun getStatistics(tunnelId: Int): TunnelStatistics?

    fun setBackendMode(backendMode: BackendMode)

    fun getBackendMode(): BackendMode

    fun handleDnsReresolve(tunnelConfig: TunnelConfig): Boolean

    /**
     * Forces the tunnel's UDP socket to rebind to the current network. Used after WiFi roaming when
     * the endpoint IP hasn't changed but the socket is still bound to the old network path.
     *
     * @return true if rebind was successful, false otherwise
     */
    suspend fun forceSocketRebind(tunnelConfig: TunnelConfig): Boolean

    suspend fun runningTunnelNames(): Set<String>

    suspend fun forceStopTunnel(tunnelId: Int)
}
