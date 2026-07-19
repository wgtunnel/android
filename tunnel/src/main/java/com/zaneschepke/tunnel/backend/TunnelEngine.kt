package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.state.EngineStartResult
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.parser.PeerSection

internal interface TunnelEngine {

    suspend fun start(
        tunnelId: Int,
        mode: BackendMode,
        splitDnsDomains: Set<String> = emptySet(),
    ): EngineStartResult

    suspend fun stop(handle: Int, mode: BackendMode)

    suspend fun updatePeers(handle: Int, mode: BackendMode, peers: List<PeerSection>)

    suspend fun getActiveConfig(handle: Int, mode: BackendMode): ActiveConfig?

    /**
     * Pushes the underlying network's DNS servers to an active VPN tunnel's split DNS resolver,
     * e.g. after the device roams between cell and wifi. No-op for tunnels without split DNS.
     */
    fun updateSplitDnsServers(handle: Int, servers: List<String>)
}
