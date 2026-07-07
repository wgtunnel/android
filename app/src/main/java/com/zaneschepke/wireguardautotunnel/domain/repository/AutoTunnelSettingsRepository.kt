package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings
import kotlinx.coroutines.flow.Flow

interface AutoTunnelSettingsRepository {
    suspend fun upsert(autoTunnelSettings: AutoTunnelSettings)

    val flow: Flow<AutoTunnelSettings>

    suspend fun getAutoTunnelSettings(): AutoTunnelSettings

    suspend fun updateAutoTunnelEnabled(enabled: Boolean)

    suspend fun updateDisableOnCaptivePortal(enabled: Boolean)
}
