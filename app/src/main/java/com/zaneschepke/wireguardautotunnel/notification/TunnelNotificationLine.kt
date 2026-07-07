package com.zaneschepke.wireguardautotunnel.notification

import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState

data class TunnelNotificationLine(
    val id: Int,
    val name: String,
    val displayState: DisplayTunnelState,
)
