package com.zaneschepke.tunnel.model

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import kotlinx.coroutines.Job

sealed class TunnelCommand {

    data class Start(val tunnel: Tunnel, val mode: BackendMode) : TunnelCommand()

    data class Stop(val tunnelId: Int) : TunnelCommand()

    data class UpdateActiveConfig(val tunnelId: Int) : TunnelCommand()

    data class AttachJob(val tunnelId: Int, val job: Job) : TunnelCommand()

    data class ApplyResolvedPeers(
        val tunnelId: Int,
        val cache: Map<PublicKey, DnsBootstrapResult>,
        val peers: List<PeerSection>,
    ) : TunnelCommand()

    data class UpdatePeers(val tunnelId: Int, val preferIpv6: Boolean) : TunnelCommand()

    data class BeginDnsResolution(val tunnelId: Int) : TunnelCommand()

    data class EndDnsResolution(val tunnelId: Int) : TunnelCommand()
}
