package com.zaneschepke.wireguardautotunnel.data.repository

import com.zaneschepke.wireguardautotunnel.data.dao.TunnelConfigDao
import com.zaneschepke.wireguardautotunnel.data.mapper.toDomain
import com.zaneschepke.wireguardautotunnel.data.mapper.toEntity
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig as Domain
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTunnelRepository(private val tunnelConfigDao: TunnelConfigDao) : TunnelRepository {

    override val flow =
        tunnelConfigDao.getAllFlow().map { it.map { tunnelConfig -> tunnelConfig.toDomain() } }

    override val userTunnelsFlow =
        tunnelConfigDao.getAllTunnelsExceptGlobal().map {
            it.map { tunnelConfig -> tunnelConfig.toDomain() }
        }

    override val globalTunnelFlow: Flow<Domain?> =
        tunnelConfigDao.getGlobalTunnel().map { it?.toDomain() }

    override suspend fun getAll(): List<Domain> {
        return tunnelConfigDao.getAll().map { it.toDomain() }
    }

    override suspend fun save(tunnelConfig: Domain) {
        tunnelConfigDao.upsert(tunnelConfig.toEntity())
    }

    override suspend fun saveAll(tunnelConfigList: List<Domain>) {
        tunnelConfigDao.saveAll(tunnelConfigList.map { tunnelConfig -> tunnelConfig.toEntity() })
    }

    override suspend fun updatePrimaryTunnel(tunnelConfig: Domain?) {
        tunnelConfigDao.resetPrimaryTunnel()
        tunnelConfig?.let { save(it.copy(isPrimaryTunnel = true)) }
    }

    override suspend fun resetActiveTunnels() {
        tunnelConfigDao.resetActiveTunnels()
    }

    override suspend fun updateMobileDataTunnel(tunnelConfig: Domain?) {
        tunnelConfigDao.resetMobileDataTunnel()
        tunnelConfig?.let { save(it.copy(isMobileDataTunnel = true)) }
    }

    override suspend fun updateEthernetTunnel(tunnelConfig: Domain?) {
        tunnelConfigDao.resetEthernetTunnel()
        tunnelConfig?.let { save(it.copy(isEthernetTunnel = true)) }
    }

    override suspend fun delete(tunnelConfig: Domain) {
        tunnelConfigDao.delete(tunnelConfig.toEntity())
    }

    override suspend fun getById(id: Int): Domain? {
        return tunnelConfigDao.getById(id.toLong())?.toDomain()
    }

    override suspend fun getActive(): List<Domain> {
        return tunnelConfigDao.getActive().map { it.toDomain() }
    }

    override suspend fun getDefaultTunnel(): Domain? {
        return tunnelConfigDao.getDefaultTunnel()?.toDomain()
    }

    override suspend fun getStartTunnel(): Domain? {
        return tunnelConfigDao.getStartTunnel()?.toDomain()
    }

    override suspend fun count(): Int {
        return tunnelConfigDao.count().toInt()
    }

    override suspend fun findByTunnelName(name: String): Domain? {
        return tunnelConfigDao.getByName(name)?.toDomain()
    }

    override suspend fun findByTunnelNetworksName(name: String): List<Domain> {
        return tunnelConfigDao.findByTunnelNetworkName(name).map { it.toDomain() }
    }

    override suspend fun findByMobileDataTunnel(): List<Domain> {
        return tunnelConfigDao.findByMobileDataTunnel().map { it.toDomain() }
    }

    override suspend fun findPrimary(): List<Domain> {
        return tunnelConfigDao.findByPrimary().map { it.toDomain() }
    }

    override suspend fun delete(tunnels: List<Domain>) {
        tunnelConfigDao.delete(tunnels.map { it.toEntity() })
    }
}
