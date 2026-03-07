package com.zaneschepke.wireguardautotunnel.data.repository

import com.zaneschepke.wireguardautotunnel.data.dao.ProxySettingsDao
import com.zaneschepke.wireguardautotunnel.data.entity.ProxySettings as Entity
import com.zaneschepke.wireguardautotunnel.data.mapper.toDomain
import com.zaneschepke.wireguardautotunnel.data.mapper.toEntity
import com.zaneschepke.wireguardautotunnel.domain.model.ProxySettings as Domain
import com.zaneschepke.wireguardautotunnel.domain.repository.ProxySettingsRepository
import kotlinx.coroutines.flow.map

class RoomProxySettingsRepository(private val proxySettingsDao: ProxySettingsDao) :
    ProxySettingsRepository {

    override suspend fun upsert(proxySettings: Domain) {
        proxySettingsDao.upsert(proxySettings.toEntity())
    }

    override val flow = proxySettingsDao.getProxySettingsFlow().map { (it ?: Entity()).toDomain() }

    override suspend fun getProxySettings(): Domain {
        return (proxySettingsDao.getProxySettings() ?: Entity()).toDomain()
    }
}
