package com.zaneschepke.wireguardautotunnel.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zaneschepke.wireguardautotunnel.data.entity.AutoTunnelSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoTunnelSettingsDao {
    @Query("SELECT * FROM auto_tunnel_settings LIMIT 1")
    suspend fun getAutoTunnelSettings(): AutoTunnelSettings?

    @Upsert suspend fun upsert(autoTunnelSettings: AutoTunnelSettings)

    @Query("SELECT * FROM auto_tunnel_settings LIMIT 1")
    fun getAutoTunnelSettingsFlow(): Flow<AutoTunnelSettings?>

    @Query("UPDATE auto_tunnel_settings SET is_tunnel_enabled = :enabled")
    suspend fun updateAutoTunnelEnabled(enabled: Boolean)

    @Query("UPDATE auto_tunnel_settings SET disable_on_captive_portal = :enabled")
    suspend fun updateDisableOnCaptivePortal(enabled: Boolean)
}
