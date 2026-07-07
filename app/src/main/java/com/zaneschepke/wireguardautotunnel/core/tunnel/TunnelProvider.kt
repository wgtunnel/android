package com.zaneschepke.wireguardautotunnel.core.tunnel

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.event.TunnelEvent
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.domain.model.LockdownSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TunnelProvider {

    suspend fun startTunnel(tunnel: Tunnel, mode: BackendMode): Result<Unit>

    suspend fun stopTunnel(tunnelId: Int): Result<Unit>

    suspend fun stopActiveTunnels(): Result<Unit>

    suspend fun setLockDown(settings: LockdownSettings): Result<Unit>

    suspend fun disableLockDown(): Result<Unit>

    suspend fun setSeamlessRoaming(enabled: Boolean): Result<Unit>

    val backendStatus: StateFlow<BackendStatus>

    val events: Flow<TunnelEvent>
}
