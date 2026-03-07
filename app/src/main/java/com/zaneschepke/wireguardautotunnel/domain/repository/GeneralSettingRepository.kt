package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.data.model.AppMode
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

    suspend fun updateAppMode(appMode: AppMode)
}
