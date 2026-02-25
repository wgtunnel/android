package com.zaneschepke.wireguardautotunnel.data.mapper

import com.zaneschepke.wireguardautotunnel.data.entity.MonitoringSettings as Entity
import com.zaneschepke.wireguardautotunnel.domain.model.MonitoringSettings as Domain

fun Entity.toDomain(): Domain =
    Domain(
        id = id,
        isPingEnabled = isPingEnabled,
        isPingMonitoringEnabled = isPingMonitoringEnabled,
        tunnelPingIntervalSeconds = tunnelPingIntervalSeconds,
        tunnelPingAttempts = tunnelPingAttempts,
        tunnelPingTimeoutSeconds = tunnelPingTimeoutSeconds,
        showDetailedPingStats = showDetailedPingStats,
        isLocalLogsEnabled = isLocalLogsEnabled,
        isRestartOnHandshakeTimeoutEnabled = isRestartOnHandshakeTimeoutEnabled,
        maxHandshakeRestartAttempts = maxHandshakeRestartAttempts,
        restartCooldownSeconds = restartCooldownSeconds,
        isRecoveryNotificationEnabled = isRecoveryNotificationEnabled,
        maxAttemptsAction = maxAttemptsAction,
        pingFailuresBeforeRestart = pingFailuresBeforeRestart,
    )

fun Domain.toEntity(): Entity =
    Entity(
        id = id,
        isPingEnabled = isPingEnabled,
        isPingMonitoringEnabled = isPingMonitoringEnabled,
        tunnelPingIntervalSeconds = tunnelPingIntervalSeconds,
        tunnelPingAttempts = tunnelPingAttempts,
        tunnelPingTimeoutSeconds = tunnelPingTimeoutSeconds,
        showDetailedPingStats = showDetailedPingStats,
        isLocalLogsEnabled = isLocalLogsEnabled,
        isRestartOnHandshakeTimeoutEnabled = isRestartOnHandshakeTimeoutEnabled,
        maxHandshakeRestartAttempts = maxHandshakeRestartAttempts,
        restartCooldownSeconds = restartCooldownSeconds,
        isRecoveryNotificationEnabled = isRecoveryNotificationEnabled,
        maxAttemptsAction = maxAttemptsAction,
        pingFailuresBeforeRestart = pingFailuresBeforeRestart,
    )
