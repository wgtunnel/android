package com.zaneschepke.tunnel.model

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.wireguardautotunnel.parser.PeerSection

typealias PublicKey = String

data class RunningTunnel(
    val handle: Int,
    val interfaceName: String,
    val tunnel: Tunnel,
    val mode: BackendMode,
    val currentPreferIpv6: Boolean = false,
    val resolvedPeers: List<PeerSection>? = null,
    val peerBootstrapCache: Map<PublicKey, DnsBootstrapResult> = emptyMap(),
)
