package com.zaneschepke.tunnel.model

import com.zaneschepke.tunnel.Tunnel
import kotlinx.coroutines.Job

typealias PublicKey = String
data class RunningTunnel(
    val handle: Int,
    val interfaceName: String,
    val tunnel: Tunnel,
    val mode: BackendMode,
    val job: Job? = null,
    val currentPreferIpv6: Boolean = false,
    val peerBootstrapCache: Map<PublicKey, DnsBootstrapResult> = emptyMap(),
)