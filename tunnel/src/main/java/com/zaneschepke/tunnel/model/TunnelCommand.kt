package com.zaneschepke.tunnel.model

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.wireguardautotunnel.parser.PeerSection

internal sealed class TunnelCommand {
    data class Start(val tunnel: Tunnel, val mode: BackendMode) : TunnelCommand()
    data class Stop(val tunnelId: Int) : TunnelCommand()
    data class UpdatePeers(val tunnelId: Int, val transform: (RunningTunnel) -> List<PeerSection>) : TunnelCommand()
    data class StatusEvent(val handle: Int, val state: Tunnel.State) : TunnelCommand()
}