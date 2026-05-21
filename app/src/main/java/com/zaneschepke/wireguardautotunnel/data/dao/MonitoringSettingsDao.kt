package com.zaneschepke.wireguardautotunnel.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zaneschepke.wireguardautotunnel.data.entity.MonitoringSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoringSettingsDao {
    @Query("SELECT * FROM monitoring_settings LIMIT 1")
    suspend fun getMonitoringSettings(): MonitoringSettings?

    @Upsert suspend fun upsert(monitoringSettings: MonitoringSettings)

    @Query("SELECT * FROM monitoring_settings LIMIT 1")
    fun getMonitoringSettingsFlow(): Flow<MonitoringSettings?>

    @Query(
        """
    UPDATE monitoring_settings
    SET tunnel_statistics_poll_interval = :interval
    WHERE id = (
        SELECT id FROM monitoring_settings LIMIT 1
    )
"""
    )
    suspend fun updateStatisticsInterval(interval: Int)

    @Query(
        """
    UPDATE monitoring_settings
    SET tunnel_statistics_enabled = :enabled
    WHERE id = (
        SELECT id FROM monitoring_settings LIMIT 1
    )
"""
    )
    suspend fun updateStatisticsEnabled(enabled: Boolean)
}
