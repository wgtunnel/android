package com.zaneschepke.tunnel.state

data class ActorState(
    val byTunnelId: Map<Int, TunnelRuntimeState>,
    val byHandle: Map<Int, Int>,
    val killSwitchEnabled: Boolean = false,
)
