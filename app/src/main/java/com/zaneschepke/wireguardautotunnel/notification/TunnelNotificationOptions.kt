package com.zaneschepke.wireguardautotunnel.notification

data class TunnelNotificationOptions(
    val liveUpdatesEnabled: Boolean = false,
    val showOrigin: Boolean = false,
    val showTransfer: Boolean = false,
    val showRecovery: Boolean = false,
    val showFailureTint: Boolean = false,
)
