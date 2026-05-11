package com.zaneschepke.tunnel.state

import com.zaneschepke.tunnel.model.BackendMode

data class EngineStartResult(
    val tunnelId: Int,
    val handle: Int,
    val interfaceName: String,
    val mode: BackendMode,
)
