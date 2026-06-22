package com.zaneschepke.wireguardautotunnel.data.mapper

import com.zaneschepke.wireguardautotunnel.data.entity.MonitoringSettings as Entity
import com.zaneschepke.wireguardautotunnel.domain.model.MonitoringSettings as Domain

fun Entity.toDomain(): Domain =
    Domain(
        id = id,
        tunnelStatisticsEnabled = tunnelStatisticsEnabled,
        tunnelStatisticsPollInterval = tunnelStatisticsPollInterval,
        isLocalLogsEnabled = isLocalLogsEnabled,
    )

fun Domain.toEntity(): Entity =
    Entity(
        id = id,
        tunnelStatisticsEnabled = tunnelStatisticsEnabled,
        tunnelStatisticsPollInterval = tunnelStatisticsPollInterval,
        isLocalLogsEnabled = isLocalLogsEnabled,
    )
