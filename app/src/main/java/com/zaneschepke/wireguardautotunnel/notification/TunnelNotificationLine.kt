package com.zaneschepke.wireguardautotunnel.notification

import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState

data class TunnelNotificationLine(
    val id: Int,
    val name: String,
    val displayState: DisplayTunnelState,
    val startedAtMillis: Long?,
    val origin: TunnelActionSource?,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val recoveryAttempts: Int = 0,
    val lockdown: Boolean = false,
)
