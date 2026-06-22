package com.zaneschepke.tunnel.service

import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.state.BackendStatus

fun BackendStatus.toNotificationComparisonKey(): Any =
    activeTunnels.mapValues { (_, tunnel) ->
        Triple(
            tunnel.transportState,
            tunnel.bootstrapState,
            tunnel.mode is BackendMode.Vpn || tunnel.mode is BackendMode.Proxy.KillSwitchPrimary,
        )
    } to (activeTunnels.keys to (killSwitch.enabled to dnsMode))
