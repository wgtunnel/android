package com.zaneschepke.wireguardautotunnel.data.repository

import com.zaneschepke.wireguardautotunnel.data.dao.GeneralSettingsDao
import com.zaneschepke.wireguardautotunnel.data.entity.GeneralSettings as Entity
import com.zaneschepke.wireguardautotunnel.data.mapper.toDomain
import com.zaneschepke.wireguardautotunnel.data.mapper.toEntity
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings as Domain
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.ui.theme.Theme
import kotlinx.coroutines.flow.map

class RoomSettingsRepository(private val settingsDao: GeneralSettingsDao) :
    GeneralSettingRepository {
    override suspend fun upsert(generalSettings: Domain) {
        settingsDao.upsert(generalSettings.toEntity())
    }

    override val flow = settingsDao.getGeneralSettingsFlow().map { (it ?: Entity()).toDomain() }

    override suspend fun getGeneralSettings(): Domain {
        return (settingsDao.getGeneralSettings() ?: Entity()).toDomain()
    }

    override suspend fun updateTheme(theme: Theme) {
        settingsDao.updateTheme(theme.name)
    }

    override suspend fun updateLocale(locale: String) {
        settingsDao.updateLocale(locale)
    }

    override suspend fun updatePinLockEnabled(enabled: Boolean) {
        settingsDao.updatePinLockEnabled(enabled)
    }

    override suspend fun updateAppMode(appMode: AppMode) {
        settingsDao.updateAppMode(appMode)
    }
}
