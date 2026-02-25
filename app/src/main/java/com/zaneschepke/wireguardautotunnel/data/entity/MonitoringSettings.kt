package com.zaneschepke.wireguardautotunnel.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zaneschepke.wireguardautotunnel.data.model.MaxAttemptsAction

@Entity(tableName = "monitoring_settings")
data class MonitoringSettings(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "is_ping_enabled", defaultValue = "0") val isPingEnabled: Boolean = false,
    @ColumnInfo(name = "is_ping_monitoring_enabled", defaultValue = "1")
    val isPingMonitoringEnabled: Boolean = true,
    @ColumnInfo(name = "tunnel_ping_interval_sec", defaultValue = "30")
    val tunnelPingIntervalSeconds: Int = 30,
    @ColumnInfo(name = "tunnel_ping_attempts", defaultValue = "3") val tunnelPingAttempts: Int = 3,
    @ColumnInfo(name = "tunnel_ping_timeout_sec") val tunnelPingTimeoutSeconds: Int? = null,
    @ColumnInfo(name = "show_detailed_ping_stats", defaultValue = "0")
    val showDetailedPingStats: Boolean = false,
    @ColumnInfo(name = "is_local_logs_enabled", defaultValue = "0")
    val isLocalLogsEnabled: Boolean = false,
    @ColumnInfo(name = "is_restart_on_handshake_timeout_enabled", defaultValue = "0")
    val isRestartOnHandshakeTimeoutEnabled: Boolean = false,
    @ColumnInfo(name = "max_handshake_restart_attempts", defaultValue = "5")
    val maxHandshakeRestartAttempts: Int = 5,
    @ColumnInfo(name = "restart_cooldown_seconds", defaultValue = "30")
    val restartCooldownSeconds: Int = 30,
    @ColumnInfo(name = "is_recovery_notification_enabled", defaultValue = "1")
    val isRecoveryNotificationEnabled: Boolean = true,
    @ColumnInfo(name = "max_attempts_action", defaultValue = "0")
    val maxAttemptsAction: MaxAttemptsAction = MaxAttemptsAction.DO_NOTHING,
)
