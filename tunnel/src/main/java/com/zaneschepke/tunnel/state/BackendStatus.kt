package com.zaneschepke.tunnel.state

data class BackendStatus(
    val killSwitch: KillSwitchState = KillSwitchState(),
    val activeTunnels: Map<Int, ActiveTunnel> = emptyMap()
)