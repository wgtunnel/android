package com.zaneschepke.tunnel.state

import com.zaneschepke.tunnel.model.RunningTunnel

data class TunnelRuntimeState(val running: RunningTunnel, val active: ActiveTunnel)
