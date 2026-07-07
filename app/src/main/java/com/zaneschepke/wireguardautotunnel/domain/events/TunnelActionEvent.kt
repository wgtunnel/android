package com.zaneschepke.wireguardautotunnel.domain.events

import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource

sealed interface TunnelActionEvent {

    val source: TunnelActionSource
    val tunnelId: Int

    data class Started(override val tunnelId: Int, override val source: TunnelActionSource) :
        TunnelActionEvent

    data class Stopped(override val tunnelId: Int, override val source: TunnelActionSource) :
        TunnelActionEvent
}
