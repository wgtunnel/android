package com.zaneschepke.wireguardautotunnel.data.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

val MIGRATION_23_24 =
    fun(dataStore: DataStore<Preferences>): Migration {
        return object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Timber.d("Starting migration from 23 to 24")
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `general_settings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `is_shortcuts_enabled` INTEGER NOT NULL DEFAULT 0,
                `is_restore_on_boot_enabled` INTEGER NOT NULL DEFAULT 0,
                `is_multi_tunnel_enabled` INTEGER NOT NULL DEFAULT 0,
                `is_tunnel_globals_enabled` INTEGER NOT NULL DEFAULT 0,
                `app_mode` INTEGER NOT NULL DEFAULT 0,
                `theme` TEXT NOT NULL DEFAULT 'AUTOMATIC',
                `locale` TEXT,
                `remote_key` TEXT,
                `is_remote_control_enabled` INTEGER NOT NULL DEFAULT 0,
                `is_pin_lock_enabled` INTEGER NOT NULL DEFAULT 0,
                `is_always_on_vpn_enabled` INTEGER NOT NULL DEFAULT 0,
                `is_lan_on_kill_switch_enabled` INTEGER NOT NULL DEFAULT 0
            )
            """
                )

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `auto_tunnel_settings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `is_tunnel_enabled` INTEGER NOT NULL DEFAULT 0,
                `is_tunnel_on_mobile_data_enabled` INTEGER NOT NULL DEFAULT 0,
                `trusted_network_ssids` TEXT NOT NULL DEFAULT '',
                `is_tunnel_on_ethernet_enabled` INTEGER NOT NULL DEFAULT 0,
                `is_tunnel_on_wifi_enabled` INTEGER NOT NULL DEFAULT 0,
                `is_wildcards_enabled` INTEGER NOT NULL DEFAULT 0,
                `is_stop_on_no_internet_enabled` INTEGER NOT NULL DEFAULT 0,
                `debounce_delay_seconds` INTEGER NOT NULL DEFAULT 3,
                `is_tunnel_on_unsecure_enabled` INTEGER NOT NULL DEFAULT 0,
                `wifi_detection_method` INTEGER NOT NULL DEFAULT 0
            )
            """
                )

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `monitoring_settings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `is_ping_enabled` INTEGER NOT NULL DEFAULT 0,
                `is_ping_monitoring_enabled` INTEGER NOT NULL DEFAULT 1,
                `tunnel_ping_interval_sec` INTEGER NOT NULL DEFAULT 30,
                `tunnel_ping_attempts` INTEGER NOT NULL DEFAULT 3,
                `tunnel_ping_timeout_sec` INTEGER,
                `show_detailed_ping_stats` INTEGER NOT NULL DEFAULT 0,
                `is_local_logs_enabled` INTEGER NOT NULL DEFAULT 0
            )
            """
                )

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `dns_settings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `dns_protocol` INTEGER NOT NULL DEFAULT 0,
                `dns_endpoint` TEXT
            )
            """
                )

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `tunnel_config` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `wg_quick` TEXT NOT NULL,
                `tunnel_networks` TEXT NOT NULL DEFAULT '',
                `is_mobile_data_tunnel` INTEGER NOT NULL DEFAULT false,
                `is_primary_tunnel` INTEGER NOT NULL DEFAULT false,
                `am_quick` TEXT NOT NULL DEFAULT '',
                `is_Active` INTEGER NOT NULL DEFAULT false,
                `restart_on_ping_failure` INTEGER NOT NULL DEFAULT false,
                `ping_target` TEXT DEFAULT null,
                `is_ethernet_tunnel` INTEGER NOT NULL DEFAULT false,
                `is_ipv4_preferred` INTEGER NOT NULL DEFAULT true,
                `position` INTEGER NOT NULL DEFAULT 0,
                `auto_tunnel_apps` TEXT NOT NULL DEFAULT '[]'
            )
            """
                )

                db.execSQL(
                    """
            CREATE UNIQUE INDEX `index_tunnel_config_name` ON `tunnel_config` (`name`)
            """
                )

                try {
                    db.execSQL(
                        """
                INSERT INTO `general_settings` (
                    `id`, `is_shortcuts_enabled`, `is_restore_on_boot_enabled`,
                    `is_multi_tunnel_enabled`, `is_tunnel_globals_enabled`, `app_mode`,
                    `is_always_on_vpn_enabled`, `is_lan_on_kill_switch_enabled`
                )
                SELECT 
                    `id`, 
                    COALESCE(`is_shortcuts_enabled`, 0),
                    COALESCE(`is_restore_on_boot_enabled`, 0),
                    COALESCE(`is_multi_tunnel_enabled`, 0),
                    COALESCE(`is_tunnel_globals_enabled`, 0),
                    COALESCE(`app_mode`, 0),
                    COALESCE(`is_always_on_vpn_enabled`, 0),
                    COALESCE(`is_lan_on_kill_switch_enabled`, 0)
                FROM `Settings`
                """
                    )
                    Timber.d("Migrated data to general_settings")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to migrate data to general_settings, inserting default row")
                    db.execSQL("INSERT INTO `general_settings` DEFAULT VALUES")
                }

                try {
                    db.execSQL(
                        """
                INSERT INTO `auto_tunnel_settings` (
                    `id`, `is_tunnel_enabled`, `is_tunnel_on_mobile_data_enabled`,
                    `trusted_network_ssids`, `is_tunnel_on_ethernet_enabled`,
                    `is_tunnel_on_wifi_enabled`, `is_wildcards_enabled`, `is_stop_on_no_internet_enabled`,
                    `debounce_delay_seconds`, `is_tunnel_on_unsecure_enabled`,
                    `wifi_detection_method`
                )
                SELECT 
                    `id`, 
                    COALESCE(`is_tunnel_enabled`, 0),
                    COALESCE(`is_tunnel_on_mobile_data_enabled`, 0),
                    COALESCE(`trusted_network_ssids`, ''),
                    COALESCE(`is_tunnel_on_ethernet_enabled`, 0),
                    COALESCE(`is_tunnel_on_wifi_enabled`, 0),
                    COALESCE(`is_wildcards_enabled`, 0),
                    COALESCE(`is_stop_on_no_internet_enabled`, 0),
                    COALESCE(`debounce_delay_seconds`, 3),
                    COALESCE(`is_tunnel_on_unsecure_enabled`, 0),
                    COALESCE(`wifi_detection_method`, 0)
                FROM `Settings`
                """
                    )
                    Timber.d("Migrated data to auto_tunnel_settings")
                } catch (e: Exception) {
                    Timber.e(
                        e,
                        "Failed to migrate data to auto_tunnel_settings, inserting default row",
                    )
                    db.execSQL("INSERT INTO `auto_tunnel_settings` DEFAULT VALUES")
                }

                try {
                    db.execSQL(
                        """
                INSERT INTO `monitoring_settings` (
                    `id`, `is_ping_enabled`, `is_ping_monitoring_enabled`,
                    `tunnel_ping_interval_sec`, `tunnel_ping_attempts`, `tunnel_ping_timeout_sec`
                )
                SELECT 
                    `id`, 
                    COALESCE(`is_ping_enabled`, 0),
                    COALESCE(`is_ping_monitoring_enabled`, 1),
                    COALESCE(`tunnel_ping_interval_sec`, 30),
                    COALESCE(`tunnel_ping_attempts`, 3),
                    COALESCE(`tunnel_ping_timeout_sec`, NULL)
                FROM `Settings`
                """
                    )
                    Timber.d("Migrated data to monitoring_settings")
                } catch (e: Exception) {
                    Timber.e(
                        e,
                        "Failed to migrate data to monitoring_settings, inserting default row",
                    )
                    db.execSQL("INSERT INTO `monitoring_settings` DEFAULT VALUES")
                }

                try {
                    db.execSQL(
                        """
                INSERT INTO `dns_settings` (
                    `id`, `dns_protocol`, `dns_endpoint`
                )
                SELECT 
                    `id`, 
                    COALESCE(`dns_protocol`, 0),
                    COALESCE(`dns_endpoint`, NULL)
                FROM `Settings`
                """
                    )
                    Timber.d("Migrated data to dns_settings")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to migrate data to dns_settings, inserting default row")
                    db.execSQL("INSERT INTO `dns_settings` DEFAULT VALUES")
                }

                try {
                    db.execSQL(
                        """
                INSERT INTO `tunnel_config` (
                    `id`, `name`, `wg_quick`, `tunnel_networks`, `is_mobile_data_tunnel`,
                    `is_primary_tunnel`, `am_quick`, `is_Active`, `restart_on_ping_failure`,
                    `ping_target`, `is_ethernet_tunnel`, `is_ipv4_preferred`, `position`,
                    `auto_tunnel_apps`
                )
                SELECT 
                    `id`, `name`, `wg_quick`, `tunnel_networks`, `is_mobile_data_tunnel`,
                    `is_primary_tunnel`, `am_quick`, `is_Active`, `restart_on_ping_failure`,
                    `ping_target`, `is_ethernet_tunnel`, `is_ipv4_preferred`, `position`,
                    `auto_tunnel_apps`
                FROM `TunnelConfig`
                """
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to migrate data to tunnel_config")
                }

                try {
                    runBlocking {
                        val preferences = dataStore.data.first()
                        val pinLockEnabled = booleanPreferencesKey("PIN_LOCK_ENABLED")
                        val isLocalLogsEnabled = booleanPreferencesKey("LOCAL_LOGS_ENABLED")
                        val locale = stringPreferencesKey("LOCALE")
                        val theme = stringPreferencesKey("THEME")
                        val isRemoteControlEnabled =
                            booleanPreferencesKey("IS_REMOTE_CONTROL_ENABLED")
                        val remoteKey = stringPreferencesKey("REMOTE_KEY")
                        val showDetailedPingStats =
                            booleanPreferencesKey("SHOW_DETAILED_PING_STATS")

                        val currentTheme = preferences[theme] ?: "AUTOMATIC"
                        val currentLocale = preferences[locale]
                        val currentRemoteKey = preferences[remoteKey]
                        val isRemoteEnabled = preferences[isRemoteControlEnabled] ?: false
                        val isPinLockEnabled = preferences[pinLockEnabled] ?: false
                        val detailedPingStats = preferences[showDetailedPingStats] ?: false
                        val localLogs = preferences[isLocalLogsEnabled] ?: false

                        val generalValues =
                            ContentValues().apply {
                                put("id", 1)
                                put("theme", currentTheme)
                                put("locale", currentLocale)
                                put("remote_key", currentRemoteKey)
                                put("is_remote_control_enabled", if (isRemoteEnabled) 1 else 0)
                                put("is_pin_lock_enabled", if (isPinLockEnabled) 1 else 0)
                            }
                        // Try updating first
                        val rowsAffected =
                            db.update(
                                table = "general_settings",
                                conflictAlgorithm = SQLiteDatabase.CONFLICT_REPLACE,
                                values = generalValues,
                                whereClause = "id = ?",
                                whereArgs = arrayOf("1"),
                            )

                        if (rowsAffected == 0) {
                            db.insert(
                                "general_settings",
                                SQLiteDatabase.CONFLICT_REPLACE,
                                generalValues,
                            )
                        }
                        Timber.d("Updated or inserted DataStore values in general_settings")

                        val monitoringValues =
                            ContentValues().apply {
                                put("id", 1)
                                put("show_detailed_ping_stats", if (detailedPingStats) 1 else 0)
                                put("is_local_logs_enabled", if (localLogs) 1 else 0)
                            }
                        val monitoringRowsAffected =
                            db.update(
                                table = "monitoring_settings",
                                conflictAlgorithm = SQLiteDatabase.CONFLICT_REPLACE,
                                values = monitoringValues,
                                whereClause = "id = ?",
                                whereArgs = arrayOf("1"),
                            )
                        if (monitoringRowsAffected == 0) {
                            db.insert(
                                "monitoring_settings",
                                SQLiteDatabase.CONFLICT_REPLACE,
                                monitoringValues,
                            )
                        }
                        Timber.d("Updated or inserted DataStore values in monitoring_settings")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to migrate datastore data")
                }

                db.execSQL("DROP TABLE IF EXISTS `Settings`")
                db.execSQL("DROP TABLE IF EXISTS `TunnelConfig`")

                Timber.d("Migration 23 to 24 completed")
            }
        }
    }

val MIGRATION_25_26 =
    object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `lockdown_settings` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `bypass_lan` INTEGER NOT NULL DEFAULT 0,
                    `metered` INTEGER NOT NULL DEFAULT 0,
                    `dual_stack` INTEGER NOT NULL DEFAULT 0
                )
                """
                    .trimIndent()
            )

            val cursor =
                db.query("SELECT `is_lan_on_kill_switch_enabled` FROM `general_settings` LIMIT 1")
            var bypassLan = 0
            if (cursor.moveToFirst()) {
                bypassLan = if (cursor.getInt(0) != 0) 1 else 0
            }
            cursor.close()

            db.execSQL(
                """
                INSERT INTO `lockdown_settings` (`bypass_lan`, `metered`, `dual_stack`)
                VALUES (?, 0, 0)
                """
                    .trimIndent(),
                arrayOf(bypassLan),
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `general_settings_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `is_shortcuts_enabled` INTEGER NOT NULL DEFAULT 0,
                    `is_restore_on_boot_enabled` INTEGER NOT NULL DEFAULT 0,
                    `is_multi_tunnel_enabled` INTEGER NOT NULL DEFAULT 0,
                    `is_tunnel_globals_enabled` INTEGER NOT NULL DEFAULT 0,
                    `app_mode` INTEGER NOT NULL DEFAULT 0,
                    `theme` TEXT NOT NULL DEFAULT 'AUTOMATIC',
                    `locale` TEXT,
                    `remote_key` TEXT,
                    `is_remote_control_enabled` INTEGER NOT NULL DEFAULT 0,
                    `is_pin_lock_enabled` INTEGER NOT NULL DEFAULT 0,
                    `is_always_on_vpn_enabled` INTEGER NOT NULL DEFAULT 0,
                    `custom_split_packages` TEXT NOT NULL DEFAULT '{}'
                )
                """
                    .trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO `general_settings_new` (
                    `id`,
                    `is_shortcuts_enabled`,
                    `is_restore_on_boot_enabled`,
                    `is_multi_tunnel_enabled`,
                    `is_tunnel_globals_enabled`,
                    `app_mode`,
                    `theme`,
                    `locale`,
                    `remote_key`,
                    `is_remote_control_enabled`,
                    `is_pin_lock_enabled`,
                    `is_always_on_vpn_enabled`,
                    `custom_split_packages`
                )
                SELECT
                    `id`,
                    `is_shortcuts_enabled`,
                    `is_restore_on_boot_enabled`,
                    `is_multi_tunnel_enabled`,
                    `is_tunnel_globals_enabled`,
                    `app_mode`,
                    `theme`,
                    `locale`,
                    `remote_key`,
                    `is_remote_control_enabled`,
                    `is_pin_lock_enabled`,
                    `is_always_on_vpn_enabled`,
                    `custom_split_packages`
                FROM `general_settings`
                """
                    .trimIndent()
            )

            db.execSQL("DROP TABLE `general_settings`")

            db.execSQL("ALTER TABLE `general_settings_new` RENAME TO `general_settings`")
        }
    }

val MIGRATION_28_29 =
    object : Migration(28, 29) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Migrate tunnel_config table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tunnel_config_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `wg_quick` TEXT NOT NULL,
                    `tunnel_networks` TEXT NOT NULL DEFAULT '',
                    `is_mobile_data_tunnel` INTEGER NOT NULL DEFAULT false,
                    `is_primary_tunnel` INTEGER NOT NULL DEFAULT false,
                    `am_quick` TEXT NOT NULL DEFAULT '',
                    `is_Active` INTEGER NOT NULL DEFAULT false,
                    `restart_on_ping_failure` INTEGER NOT NULL DEFAULT false,
                    `ping_target` TEXT DEFAULT null,
                    `is_ethernet_tunnel` INTEGER NOT NULL DEFAULT false,
                    `is_ipv4_preferred` INTEGER NOT NULL DEFAULT true,
                    `position` INTEGER NOT NULL DEFAULT 0,
                    `auto_tunnel_apps` TEXT NOT NULL DEFAULT '[]',
                    `is_metered` INTEGER NOT NULL DEFAULT false 
                )
                """
                    .trimIndent()
            )

            database.execSQL(
                """
                INSERT INTO `tunnel_config_new` (
                    `id`, `name`, `wg_quick`, `tunnel_networks`, `is_mobile_data_tunnel`,
                    `is_primary_tunnel`, `am_quick`, `is_Active`, `restart_on_ping_failure`,
                    `ping_target`, `is_ethernet_tunnel`, `is_ipv4_preferred`, `position`,
                    `auto_tunnel_apps`, `is_metered`
                )
                SELECT
                    `id`, `name`, `wg_quick`, `tunnel_networks`, `is_mobile_data_tunnel`,
                    `is_primary_tunnel`, `am_quick`, `is_Active`, `restart_on_ping_failure`,
                    `ping_target`, `is_ethernet_tunnel`, `is_ipv4_preferred`, `position`,
                    `auto_tunnel_apps`, 0 AS `is_metered`
                FROM `tunnel_config`
                """
                    .trimIndent()
            )

            database.execSQL("DROP TABLE `tunnel_config`")
            database.execSQL("ALTER TABLE `tunnel_config_new` RENAME TO `tunnel_config`")
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_tunnel_config_name` ON `tunnel_config` (`name`)"
            )
        }
    }
