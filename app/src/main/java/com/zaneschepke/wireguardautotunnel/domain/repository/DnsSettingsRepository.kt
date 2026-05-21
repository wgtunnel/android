package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.model.DnsSettings
import kotlinx.coroutines.flow.Flow

interface DnsSettingsRepository {
    suspend fun upsert(dnsSettings: DnsSettings)

    val flow: Flow<DnsSettings>

    suspend fun getDnsSettings(): DnsSettings

    suspend fun updateGlobalDnsEnabled(enabled: Boolean)
}
