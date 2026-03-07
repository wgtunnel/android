package com.zaneschepke.wireguardautotunnel.data.repository

import com.zaneschepke.wireguardautotunnel.data.dao.MonitoringSettingsDao
import com.zaneschepke.wireguardautotunnel.data.entity.MonitoringSettings as Entity
import com.zaneschepke.wireguardautotunnel.data.mapper.toDomain
import com.zaneschepke.wireguardautotunnel.data.mapper.toEntity
import com.zaneschepke.wireguardautotunnel.domain.model.MonitoringSettings as Domain
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMonitoringSettingsRepository(private val monitoringSettingsDao: MonitoringSettingsDao) :
    MonitoringSettingsRepository {
    override suspend fun upsert(monitoringSettings: Domain) {
        monitoringSettingsDao.upsert(monitoringSettings.toEntity())
    }

    override val flow: Flow<Domain>
        get() =
            monitoringSettingsDao.getMonitoringSettingsFlow().map { (it ?: Entity()).toDomain() }

    override suspend fun getMonitoringSettings(): Domain {
        return (monitoringSettingsDao.getMonitoringSettings() ?: Entity()).toDomain()
    }
}
