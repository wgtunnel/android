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
        maxRestartAttempts = maxRestartAttempts,
        restartCooldownSeconds = restartCooldownSeconds,
        maxAttemptsAction = maxAttemptsAction,
        pingFailuresBeforeRestart = pingFailuresBeforeRestart,
        isBackoffEnabled = isBackoffEnabled,
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
        maxRestartAttempts = maxRestartAttempts,
        restartCooldownSeconds = restartCooldownSeconds,
        maxAttemptsAction = maxAttemptsAction,
        pingFailuresBeforeRestart = pingFailuresBeforeRestart,
        isBackoffEnabled = isBackoffEnabled,
    )
