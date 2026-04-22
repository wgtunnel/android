package com.zaneschepke.tunnel.model

import com.zaneschepke.tunnel.Tunnel
import kotlinx.coroutines.Job

data class RunningTunnel(
    val handle: Int,
    val interfaceName: String,
    val tunnel: Tunnel,
    val mode: BackendMode,
    val job: Job? = null
)