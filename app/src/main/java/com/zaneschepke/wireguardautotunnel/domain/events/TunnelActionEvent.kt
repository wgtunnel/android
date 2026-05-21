package com.zaneschepke.wireguardautotunnel.domain.events

import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource

sealed interface TunnelActionEvent {

    data class Started(val tunnelId: Int, val source: TunnelActionSource) : TunnelActionEvent

    data class Stopped(val tunnelId: Int, val source: TunnelActionSource) : TunnelActionEvent
}
