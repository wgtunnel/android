package com.zaneschepke.wireguardautotunnel.data.repository

import com.zaneschepke.wireguardautotunnel.data.dao.LockdownSettingsDao
import com.zaneschepke.wireguardautotunnel.data.entity.LockdownSettings as Entity
import com.zaneschepke.wireguardautotunnel.data.mapper.toDomain
import com.zaneschepke.wireguardautotunnel.data.mapper.toEntity
import com.zaneschepke.wireguardautotunnel.domain.model.LockdownSettings as Domain
import com.zaneschepke.wireguardautotunnel.domain.repository.LockdownSettingsRepository
import kotlinx.coroutines.flow.map

class RoomLockdownSettingsRepository(private val lockdownSettingsDao: LockdownSettingsDao) :
    LockdownSettingsRepository {
    override suspend fun upsert(lockdownSettings: Domain) {
        lockdownSettingsDao.upsert(lockdownSettings.toEntity())
    }

    override val flow =
        lockdownSettingsDao.getLockdownSettingsFlow().map { (it ?: Entity()).toDomain() }

    override suspend fun getLockdownSettings(): Domain {
        return (lockdownSettingsDao.getLockdownSettings() ?: Entity()).toDomain()
    }
}
