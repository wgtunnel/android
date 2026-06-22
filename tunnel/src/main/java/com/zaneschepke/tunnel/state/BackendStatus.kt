package com.zaneschepke.tunnel.state

import com.zaneschepke.tunnel.model.DnsBoostrapMode

data class BackendStatus(
    val killSwitch: KillSwitchState = KillSwitchState(),
    val activeTunnels: Map<Int, ActiveTunnel> = emptyMap(),
    val dnsMode: DnsBoostrapMode = DnsBoostrapMode.System,
)
