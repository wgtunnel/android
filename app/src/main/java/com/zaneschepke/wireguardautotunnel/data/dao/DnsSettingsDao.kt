package com.zaneschepke.wireguardautotunnel.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zaneschepke.wireguardautotunnel.data.entity.DnsSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface DnsSettingsDao {
    @Query("SELECT * FROM dns_settings LIMIT 1") suspend fun getDnsSettings(): DnsSettings?

    @Upsert suspend fun upsert(dnsSettings: DnsSettings)

    @Query("SELECT * FROM dns_settings LIMIT 1") fun getDnsSettingsFlow(): Flow<DnsSettings?>

    @Query("UPDATE dns_settings SET global_tunnel_dns_enabled = :enabled")
    suspend fun updateGlobalDnsEnabled(enabled: Boolean)
}
