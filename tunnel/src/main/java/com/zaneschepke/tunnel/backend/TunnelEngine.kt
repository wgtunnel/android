package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.state.EngineStartResult
import com.zaneschepke.tunnel.state.EngineState
import com.zaneschepke.tunnel.state.NativeTunnelStatus
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import kotlinx.coroutines.flow.Flow

internal interface TunnelEngine {

    val status: Flow<NativeTunnelStatus>
    val state: Flow<EngineState>

    suspend fun start(tunnel: Tunnel, mode: BackendMode): EngineStartResult

    suspend fun stop(handle: Int, mode: BackendMode)

    suspend fun updatePeers(handle: Int, mode: BackendMode, peers: List<PeerSection>)

    suspend fun getActiveConfig(handle: Int, mode: BackendMode): ActiveConfig?
}
