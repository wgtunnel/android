package com.zaneschepke.wireguardautotunnel.domain.model

import com.zaneschepke.wireguardautotunnel.data.model.MaxAttemptsAction

data class MonitoringSettings(
    val id: Int = 0,
    val isPingEnabled: Boolean = false,
    val isPingMonitoringEnabled: Boolean = true,
    val tunnelPingIntervalSeconds: Int = 30,
    val tunnelPingAttempts: Int = 3,
    val tunnelPingTimeoutSeconds: Int? = null,
    val showDetailedPingStats: Boolean = false,
    val isLocalLogsEnabled: Boolean = false,
    val isRestartOnHandshakeTimeoutEnabled: Boolean = false,
    val maxRestartAttempts: Int = 5,
    val restartCooldownSeconds: Int = 30,
    val maxAttemptsAction: MaxAttemptsAction = MaxAttemptsAction.DO_NOTHING,
    val pingFailuresBeforeRestart: Int = 1,
    val isBackoffEnabled: Boolean = false,
    val isFallbackEnabled: Boolean = false,
    val defaultFallbackTunnelId: Int? = null,
)
