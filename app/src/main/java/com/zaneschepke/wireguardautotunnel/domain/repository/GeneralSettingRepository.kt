package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.ui.theme.Theme
import kotlinx.coroutines.flow.Flow

interface GeneralSettingRepository {
    suspend fun upsert(generalSettings: GeneralSettings)

    val flow: Flow<GeneralSettings>

    suspend fun getGeneralSettings(): GeneralSettings

    suspend fun updateTheme(theme: Theme)

    suspend fun updateLocale(locale: String)

    suspend fun updatePinLockEnabled(enabled: Boolean)

    suspend fun updateAppMode(tunnelMode: TunnelMode)

    suspend fun updateGlobalAmneziaEnabled(enabled: Boolean)

    suspend fun updateScreenRecordingSecurity(enabled: Boolean)

    suspend fun updateSeamlessRoaming(enabled: Boolean)
}
