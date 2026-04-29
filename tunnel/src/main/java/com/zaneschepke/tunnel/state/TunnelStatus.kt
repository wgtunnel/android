package com.zaneschepke.tunnel.state

import com.zaneschepke.tunnel.Tunnel

data class TunnelStatus(
    val handle: Int, val interfaceName: String, val statusCode: Int
) {
    // maps to native side status codes
    fun asTunnelState(): Tunnel.State {
        return when (statusCode) {
            0 -> Tunnel.State.Up.Healthy
            1 -> Tunnel.State.Up.HandshakeFailure
            2 -> Tunnel.State.Up.ResolvingDns
            else -> Tunnel.State.Down
        }
    }
}