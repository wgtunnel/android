package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.ApplicationProvider
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.event.TunnelEvent
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.DnsBoostrapMode
import com.zaneschepke.tunnel.model.KillSwitchConfig
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.tunnel.state.BackendStatus
import kotlinx.coroutines.flow.Flow

interface Backend {

    val applicationProvider: ApplicationProvider

    suspend fun start(tunnel: Tunnel, mode: BackendMode): Result<Unit>

    fun setAlwaysOnCallback(alwaysOnCallback: VpnService.AlwaysOnCallback)

    suspend fun stop(id: Int): Result<Unit>

    suspend fun setKillSwitch(config: KillSwitchConfig): Result<Unit>

    suspend fun disableKillSwitch(): Result<Unit>

    suspend fun setBootstrapDnsMode(mode: DnsBoostrapMode)

    suspend fun stopAllActiveTunnels(): Result<Unit>

    val status: Flow<BackendStatus>

    val events: Flow<TunnelEvent>
}
