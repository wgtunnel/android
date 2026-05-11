package com.zaneschepke.tunnel.state

internal data class EngineState(
    val tunnels: Map<Int, TunnelRuntimeState> = emptyMap(),
    val killSwitch: KillSwitchState = KillSwitchState(),
    val dns: DnsState = DnsState(),
)
