package com.zaneschepke.tunnel.model

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.state.BootstrapState
import com.zaneschepke.wireguardautotunnel.parser.PeerSection

sealed class TunnelCommand {

    data class Start(val tunnel: Tunnel, val mode: BackendMode) : TunnelCommand()

    data class Stop(val tunnelId: Int) : TunnelCommand()

    data class UpdateActiveConfig(val tunnelId: Int) : TunnelCommand()

    data class UpdateKillSwitch(val enabled: Boolean) : TunnelCommand()

    data class ApplyResolvedPeers(
        val tunnelId: Int,
        val cache: Map<PublicKey, DnsBootstrapResult>,
        val peers: List<PeerSection>,
    ) : TunnelCommand()

    data class UpdatePeers(val tunnelId: Int, val preferIpv6: Boolean) : TunnelCommand()

    data class SetBootstrapState(val tunnelId: Int, val state: BootstrapState) : TunnelCommand()

    data class RunHook(val tunnelId: Int, val phase: Phase, val cmds: List<String>?) :
        TunnelCommand() {
        enum class Phase {
            PreUp,
            PostUp,
            PreDown,
            PostDown,
        }
    }
}
