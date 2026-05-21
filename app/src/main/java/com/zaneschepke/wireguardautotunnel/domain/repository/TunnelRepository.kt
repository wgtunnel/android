package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import kotlinx.coroutines.flow.Flow

interface TunnelRepository {
    val flow: Flow<List<TunnelConfig>>

    val userTunnelsFlow: Flow<List<TunnelConfig>>

    val globalTunnelFlow: Flow<TunnelConfig?>

    suspend fun getAll(): List<TunnelConfig>

    suspend fun setMetered(tunnelId: Int, value: Boolean)

    suspend fun setDynamicDns(tunnelId: Int, value: Boolean)

    suspend fun save(tunnelConfig: TunnelConfig)

    suspend fun saveAll(tunnelConfigList: List<TunnelConfig>)

    suspend fun updatePrimaryTunnel(tunnelConfig: TunnelConfig?)

    suspend fun updateMobileDataTunnel(tunnelConfig: TunnelConfig?)

    suspend fun updateEthernetTunnel(tunnelConfig: TunnelConfig?)

    suspend fun delete(tunnelConfig: TunnelConfig)

    suspend fun getById(id: Int): TunnelConfig?

    suspend fun getDefaultTunnel(): TunnelConfig?

    suspend fun count(): Int

    suspend fun findByTunnelName(name: String): TunnelConfig?

    suspend fun findByTunnelNetworksName(name: String): List<TunnelConfig>

    suspend fun findByMobileDataTunnel(): List<TunnelConfig>

    suspend fun findPrimary(): List<TunnelConfig>

    suspend fun delete(tunnels: List<TunnelConfig>)

    suspend fun ensureGlobalConfigExists()
}
