package com.zaneschepke.wireguardautotunnel.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zaneschepke.wireguardautotunnel.data.dao.AutoTunnelSettingsDao
import com.zaneschepke.wireguardautotunnel.data.dao.DnsSettingsDao
import com.zaneschepke.wireguardautotunnel.data.dao.GeneralSettingsDao
import com.zaneschepke.wireguardautotunnel.data.dao.LockdownSettingsDao
import com.zaneschepke.wireguardautotunnel.data.dao.MonitoringSettingsDao
import com.zaneschepke.wireguardautotunnel.data.dao.ProxySettingsDao
import com.zaneschepke.wireguardautotunnel.data.dao.TunnelConfigDao
import com.zaneschepke.wireguardautotunnel.data.entity.AutoTunnelSettings
import com.zaneschepke.wireguardautotunnel.data.entity.DnsSettings
import com.zaneschepke.wireguardautotunnel.data.entity.GeneralSettings
import com.zaneschepke.wireguardautotunnel.data.entity.LockdownSettings
import com.zaneschepke.wireguardautotunnel.data.entity.MonitoringSettings
import com.zaneschepke.wireguardautotunnel.data.entity.ProxySettings
import com.zaneschepke.wireguardautotunnel.data.entity.TunnelConfig

@Database(
    entities =
        [
            TunnelConfig::class,
            ProxySettings::class,
            GeneralSettings::class,
            AutoTunnelSettings::class,
            MonitoringSettings::class,
            DnsSettings::class,
            LockdownSettings::class,
        ],
    version = 33,
    autoMigrations =
        [
            AutoMigration(from = 1, to = 2),
            AutoMigration(from = 2, to = 3),
            AutoMigration(from = 3, to = 4),
            AutoMigration(from = 4, to = 5),
            AutoMigration(from = 5, to = 6),
            AutoMigration(from = 6, to = 7, spec = RemoveLegacySettingColumnsMigration::class),
            AutoMigration(7, 8),
            AutoMigration(8, 9),
            AutoMigration(9, 10),
            AutoMigration(from = 10, to = 11, spec = RemoveTunnelPauseMigration::class),
            AutoMigration(from = 11, to = 12),
            AutoMigration(from = 12, to = 13),
            AutoMigration(from = 13, to = 14),
            AutoMigration(from = 14, to = 15),
            AutoMigration(from = 15, to = 16),
            AutoMigration(from = 16, to = 17, spec = WifiDetectionMigration::class),
            AutoMigration(from = 17, to = 18),
            AutoMigration(from = 18, to = 19, spec = PingMigration::class),
            AutoMigration(from = 19, to = 20, spec = ProxyMigration::class),
            AutoMigration(from = 20, to = 21, spec = FixProxySettingsMigration::class),
            AutoMigration(from = 21, to = 22),
            AutoMigration(from = 22, to = 23),
            AutoMigration(from = 24, to = 25),
            AutoMigration(from = 26, to = 27, spec = GlobalsMigration::class),
            AutoMigration(from = 27, to = 28, spec = DonationMigration::class),
            AutoMigration(from = 29, to = 30, spec = SingleConfigMigration::class),
            AutoMigration(from = 30, to = 31),
            AutoMigration(from = 31, to = 32),
            AutoMigration(from = 32, to = 33),
        ],
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tunnelConfigDoa(): TunnelConfigDao

    abstract fun proxySettingsDoa(): ProxySettingsDao

    abstract fun generalSettingsDao(): GeneralSettingsDao

    abstract fun autoTunnelSettingsDao(): AutoTunnelSettingsDao

    abstract fun monitoringSettingsDao(): MonitoringSettingsDao

    abstract fun lockdownSettingsDao(): LockdownSettingsDao

    abstract fun dnsSettingsDao(): DnsSettingsDao
}

@DeleteColumn(tableName = "Settings", columnName = "default_tunnel")
@DeleteColumn(tableName = "Settings", columnName = "is_battery_saver_enabled")
class RemoveLegacySettingColumnsMigration : AutoMigrationSpec

@DeleteColumn(tableName = "Settings", columnName = "is_auto_tunnel_paused")
class RemoveTunnelPauseMigration : AutoMigrationSpec

@DeleteColumn(tableName = "Settings", columnName = "is_wifi_by_shell_enabled")
class WifiDetectionMigration : AutoMigrationSpec

@DeleteColumn.Entries(
    DeleteColumn(tableName = "TunnelConfig", columnName = "ping_interval"),
    DeleteColumn(tableName = "TunnelConfig", columnName = "ping_cooldown"),
    DeleteColumn(tableName = "Settings", columnName = "split_tunnel_apps"),
)
@RenameColumn.Entries(
    RenameColumn(
        tableName = "TunnelConfig",
        fromColumnName = "is_ping_enabled",
        toColumnName = "restart_on_ping_failure",
    ),
    RenameColumn(
        tableName = "TunnelConfig",
        fromColumnName = "ping_ip",
        toColumnName = "ping_target",
    ),
)
class PingMigration : AutoMigrationSpec

@DeleteColumn.Entries(
    DeleteColumn(tableName = "Settings", columnName = "is_amnezia_enabled"),
    DeleteColumn(tableName = "Settings", columnName = "is_vpn_kill_switch_enabled"),
    DeleteColumn(tableName = "Settings", columnName = "is_kernel_kill_switch_enabled"),
    DeleteColumn(tableName = "Settings", columnName = "is_kernel_enabled"),
)
class ProxyMigration : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO proxy_settings DEFAULT VALUES")
    }
}

class FixProxySettingsMigration : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        val cursor = db.query("SELECT COUNT(*) FROM proxy_settings")
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()

        if (count == 0) {
            db.execSQL("INSERT INTO proxy_settings DEFAULT VALUES")
        }
    }
}

@RenameColumn.Entries(
    RenameColumn(
        tableName = "general_settings",
        fromColumnName = "is_tunnel_globals_enabled",
        toColumnName = "global_split_tunnel_enabled",
    )
)
class GlobalsMigration : AutoMigrationSpec

@DeleteColumn(tableName = "general_settings", columnName = "custom_split_packages")
class DonationMigration : AutoMigrationSpec

@RenameColumn.Entries(
    RenameColumn(
        tableName = "tunnel_config",
        fromColumnName = "is_ipv4_preferred",
        toColumnName = "prefer_ipv6",
    ),
    RenameColumn(
        tableName = "tunnel_config",
        fromColumnName = "am_quick",
        toColumnName = "quick_config",
    ),
    RenameColumn(
        tableName = "tunnel_config",
        fromColumnName = "restart_on_ping_failure",
        toColumnName = "dynamic_dns",
    ),
)
@DeleteColumn.Entries(
    DeleteColumn(tableName = "tunnel_config", columnName = "wg_quick"),
    DeleteColumn(tableName = "tunnel_config", columnName = "ping_target"),
    DeleteColumn(tableName = "tunnel_config", columnName = "is_Active"),
    DeleteColumn(tableName = "monitoring_settings", columnName = "is_ping_enabled"),
    DeleteColumn(tableName = "monitoring_settings", columnName = "is_ping_monitoring_enabled"),
    DeleteColumn(tableName = "monitoring_settings", columnName = "tunnel_ping_interval_sec"),
    DeleteColumn(tableName = "monitoring_settings", columnName = "tunnel_ping_attempts"),
    DeleteColumn(tableName = "monitoring_settings", columnName = "tunnel_ping_timeout_sec"),
    DeleteColumn(tableName = "monitoring_settings", columnName = "show_detailed_ping_stats"),
    DeleteColumn(tableName = "auto_tunnel_settings", columnName = "debounce_delay_seconds"),
)
class SingleConfigMigration : AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE tunnel_config
            SET prefer_ipv6 =
                CASE prefer_ipv6
                    WHEN 1 THEN 0
                    WHEN 0 THEN 1
                    ELSE 0
                END
        """
        )

        db.execSQL(
            """
            UPDATE general_settings
            SET app_mode = CASE app_mode
                WHEN 3 THEN 0
                ELSE app_mode
            END
            """
                .trimIndent()
        )
    }
}
