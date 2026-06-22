package com.zaneschepke.wireguardautotunnel.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitoring_settings")
data class MonitoringSettings(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "is_local_logs_enabled", defaultValue = "0")
    val isLocalLogsEnabled: Boolean = false,
    @ColumnInfo(name = "tunnel_statistics_enabled", defaultValue = "1")
    val tunnelStatisticsEnabled: Boolean = true,
    @ColumnInfo(name = "tunnel_statistics_poll_interval", defaultValue = "3")
    val tunnelStatisticsPollInterval: Int = 3,
)
