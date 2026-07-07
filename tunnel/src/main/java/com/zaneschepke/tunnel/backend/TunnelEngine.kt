package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.state.EngineStartResult
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.parser.PeerSection

internal interface TunnelEngine {

    suspend fun start(tunnel: Tunnel, mode: BackendMode): EngineStartResult

    suspend fun stop(handle: Int, mode: BackendMode)

    suspend fun updatePeers(handle: Int, mode: BackendMode, peers: List<PeerSection>)

    suspend fun getActiveConfig(handle: Int, mode: BackendMode): ActiveConfig?

    suspend fun updateBind(handle: Int, mode: BackendMode)
}
