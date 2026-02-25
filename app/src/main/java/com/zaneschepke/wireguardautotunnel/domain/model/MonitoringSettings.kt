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
    val maxHandshakeRestartAttempts: Int = 5,
    val restartCooldownSeconds: Int = 30,
    val isRecoveryNotificationEnabled: Boolean = true,
    val maxAttemptsAction: MaxAttemptsAction = MaxAttemptsAction.DO_NOTHING,
    val pingFailuresBeforeRestart: Int = 1,
    val isBackoffEnabled: Boolean = false,
    val backoffTimeoutMinutes: Int = 60,
)
