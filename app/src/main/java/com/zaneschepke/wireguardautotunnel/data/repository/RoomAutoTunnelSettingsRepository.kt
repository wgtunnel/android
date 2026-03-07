package com.zaneschepke.wireguardautotunnel.data.repository

import com.zaneschepke.wireguardautotunnel.data.dao.AutoTunnelSettingsDao
import com.zaneschepke.wireguardautotunnel.data.entity.AutoTunnelSettings as Entity
import com.zaneschepke.wireguardautotunnel.data.mapper.toDomain
import com.zaneschepke.wireguardautotunnel.data.mapper.toEntity
import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings as Domain
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAutoTunnelSettingsRepository(private val autoTunnelSettingsDao: AutoTunnelSettingsDao) :
    AutoTunnelSettingsRepository {
    override suspend fun upsert(autoTunnelSettings: Domain) {
        autoTunnelSettingsDao.upsert(autoTunnelSettings.toEntity())
    }

    override val flow: Flow<Domain>
        get() =
            autoTunnelSettingsDao.getAutoTunnelSettingsFlow().map { (it ?: Entity()).toDomain() }

    override suspend fun getAutoTunnelSettings(): Domain {
        return (autoTunnelSettingsDao.getAutoTunnelSettings() ?: Entity()).toDomain()
    }

    override suspend fun updateAutoTunnelEnabled(enabled: Boolean) {
        autoTunnelSettingsDao.updateAutoTunnelEnabled(enabled)
    }
}
