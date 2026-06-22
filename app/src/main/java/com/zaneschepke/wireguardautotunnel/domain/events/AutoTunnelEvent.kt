package com.zaneschepke.wireguardautotunnel.domain.events

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

sealed interface AutoTunnelEvent {

    data class Sync(val start: Set<TunnelConfig>, val stop: Set<Int>) : AutoTunnelEvent

    data object DoNothing : AutoTunnelEvent

    data object StopAllDueToNoInternet : AutoTunnelEvent
}
