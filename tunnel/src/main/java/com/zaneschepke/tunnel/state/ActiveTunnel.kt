package com.zaneschepke.tunnel.state

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig

data class ActiveTunnel(
    val tunnel: Tunnel? = null,
    val mode: BackendMode? = null,
    val transportState: Tunnel.State = Tunnel.State.Down,
    val bootstrapState: BootstrapState = BootstrapState.None,
    val lastStateChangeMs: Long = System.currentTimeMillis(),
    val lastHealthChangeMs: Long = 0L,
    val interfaceName: String? = null,
    val activeConfig: ActiveConfig? = null,
    val uptime: Long? = null,
    val lastPeerUpdateMs: Long = 0L,
    val isFallenBackToIpv4ForNetwork: Boolean = false,
) {
    val isPeerUpdating: Boolean
        get() = System.currentTimeMillis() - lastPeerUpdateMs < PEER_UPDATE_GRACE_MS

    companion object {
        private const val PEER_UPDATE_GRACE_MS = 8_000L
    }
}
