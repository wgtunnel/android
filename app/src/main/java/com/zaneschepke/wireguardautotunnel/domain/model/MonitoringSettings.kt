package com.zaneschepke.wireguardautotunnel.domain.model

data class MonitoringSettings(
    val id: Int = 0,
    val isLocalLogsEnabled: Boolean = false,
    val tunnelStatisticsEnabled: Boolean = true,
    val tunnelStatisticsPollInterval: Int = 3,
)
