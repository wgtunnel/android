package com.zaneschepke.tunnel.state

import com.zaneschepke.tunnel.model.KillSwitchConfig

data class KillSwitchState(
    val enabled: Boolean = false,
    val config: KillSwitchConfig? = null,
    val primaryTunnel: Long? = null,
)
