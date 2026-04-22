package com.zaneschepke.tunnel.state

import com.zaneschepke.pinger.model.PingStats
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig

data class ActiveTunnel(
    val state: Tunnel.State = Tunnel.State.Down,
    val interfaceName: String? = null,
    val activeConfig: ActiveConfig? = null,
    val pingStats: PingStats? = null,
    val mode: BackendMode? = null,
)