package com.zaneschepke.wireguardautotunnel.core.tunnel

import com.wgtunnel.backend.Tunnel
import com.wgtunnel.backend.event.TunnelEvent
import com.wgtunnel.backend.model.BackendMode
import com.wgtunnel.backend.model.dns.TunnelDnsConfig
import com.wgtunnel.backend.state.BackendStatus
import com.wgtunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.domain.model.LockdownSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TunnelProvider {

    suspend fun startTunnel(
        tunnel: Tunnel,
        mode: BackendMode,
        tunnelDnsConfig: TunnelDnsConfig?,
        outerConfig: Config? = null,
    ): Result<Unit>

    suspend fun stopTunnel(tunnelId: Int): Result<Unit>

    suspend fun stopActiveTunnels(): Result<Unit>

    suspend fun setLockDown(settings: LockdownSettings): Result<Unit>

    suspend fun disableLockDown(): Result<Unit>

    suspend fun updateTunnel(tunnel: Tunnel): Result<Unit>

    val backendStatus: StateFlow<BackendStatus>

    val events: Flow<TunnelEvent>
}
