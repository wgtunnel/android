package com.zaneschepke.tunnel.event

import com.zaneschepke.tunnel.model.DnsBootstrapResult
import com.zaneschepke.tunnel.model.PublicKey
import com.zaneschepke.tunnel.model.TunnelCommand
import com.zaneschepke.tunnel.state.EngineStartResult
import com.zaneschepke.tunnel.state.TunnelStatus
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import kotlinx.coroutines.Job

sealed class ActorEvent {
    data class EngineStatus(val status: TunnelStatus) : ActorEvent()

    data class TunnelStarted(val result: EngineStartResult, val cmd: TunnelCommand.Start) :
        ActorEvent()

    data class TunnelStopped(val tunnelId: Int, val handle: Int) : ActorEvent()

    data class PeersUpdated(
        val tunnelId: Int,
        val peers: List<PeerSection>,
        val preferIpv6: Boolean,
    ) : ActorEvent()

    data class ResolvedPeersApplied(
        val tunnelId: Int,
        val cache: Map<PublicKey, DnsBootstrapResult>,
        val peers: List<PeerSection>,
    ) : ActorEvent()

    data class JobAttached(val tunnelId: Int, val job: Job) : ActorEvent()

    data class ActiveConfigUpdated(val tunnelId: Int, val activeConfig: ActiveConfig?) :
        ActorEvent()

    data class DnsResolutionStarted(val tunnelId: Int) : ActorEvent()

    data class DnsResolutionFinished(val tunnelId: Int) : ActorEvent()
}
