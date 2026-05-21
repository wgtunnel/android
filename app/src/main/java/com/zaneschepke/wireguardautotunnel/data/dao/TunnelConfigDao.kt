package com.zaneschepke.wireguardautotunnel.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.zaneschepke.wireguardautotunnel.data.entity.TunnelConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface TunnelConfigDao {

    @Upsert suspend fun upsert(t: TunnelConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAll(t: List<TunnelConfig>)

    @Query("UPDATE tunnel_config SET is_metered = :value WHERE id = :id")
    suspend fun setMetered(id: Int, value: Boolean)

    @Query("UPDATE tunnel_config SET dynamic_dns = :value WHERE id = :id")
    suspend fun setDynamicDns(id: Int, value: Boolean)

    @Query("SELECT * FROM tunnel_config WHERE id=:id") suspend fun getById(id: Long): TunnelConfig?

    @Query("SELECT * FROM tunnel_config WHERE name=:name")
    suspend fun getByName(name: String): TunnelConfig?

    @Query("SELECT * FROM tunnel_config") suspend fun getAll(): List<TunnelConfig>

    @Delete suspend fun delete(t: TunnelConfig)

    @Delete suspend fun delete(t: List<TunnelConfig>)

    @Query("SELECT COUNT('id') FROM tunnel_config") suspend fun count(): Long

    @Query("SELECT * FROM tunnel_config WHERE tunnel_networks LIKE '%' || :name || '%'")
    suspend fun findByTunnelNetworkName(name: String): List<TunnelConfig>

    @Query("UPDATE tunnel_config SET is_primary_tunnel = 0 WHERE is_primary_tunnel =1")
    suspend fun resetPrimaryTunnel()

    @Query("UPDATE tunnel_config SET is_mobile_data_tunnel = 0 WHERE is_mobile_data_tunnel =1")
    suspend fun resetMobileDataTunnel()

    @Query("UPDATE tunnel_config SET is_ethernet_tunnel = 0 WHERE is_ethernet_tunnel =1")
    suspend fun resetEthernetTunnel()

    @Query("SELECT * FROM tunnel_config WHERE is_primary_tunnel=1")
    suspend fun findByPrimary(): List<TunnelConfig>

    @Query("SELECT * FROM tunnel_config WHERE is_mobile_data_tunnel=1")
    suspend fun findByMobileDataTunnel(): List<TunnelConfig>

    @Query(
        """
    SELECT *
    FROM tunnel_config
    WHERE name != '${TunnelConfig.GLOBAL_CONFIG_NAME}'
    ORDER BY is_primary_tunnel DESC, position ASC
    LIMIT 1
    """
    )
    suspend fun getDefaultTunnel(): TunnelConfig?

    @Query("SELECT * FROM tunnel_config ORDER BY position")
    fun getAllFlow(): Flow<List<TunnelConfig>>

    @Query("SELECT * FROM tunnel_config WHERE name != :globalName ORDER BY position")
    fun getAllTunnelsExceptGlobal(
        globalName: String = TunnelConfig.GLOBAL_CONFIG_NAME
    ): Flow<List<TunnelConfig>>

    @Query("SELECT * FROM tunnel_config WHERE name = :globalName LIMIT 1")
    fun getGlobalTunnel(globalName: String = TunnelConfig.GLOBAL_CONFIG_NAME): Flow<TunnelConfig?>
}
