package com.zaneschepke.tunnel.model

data class KillSwitchConfig(
    val allowedIps: Set<String>,
    val metered: Boolean,
    val dualStack: Boolean,
)
