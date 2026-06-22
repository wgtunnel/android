package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.model.MonitoringSettings
import kotlinx.coroutines.flow.Flow

interface MonitoringSettingsRepository {
    suspend fun upsert(monitoringSettings: MonitoringSettings)

    val flow: Flow<MonitoringSettings>

    suspend fun getMonitoringSettings(): MonitoringSettings

    suspend fun updateStatisticRefresh(statisticRefresh: Int)

    suspend fun updateStatisticsEnabled(enabled: Boolean)
}
