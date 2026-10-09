package com.zaneschepke.wireguardautotunnel.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.zaneschepke.wireguardautotunnel.data.entity.TunnelGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface TunnelGroupDao {

    @Upsert suspend fun upsert(group: TunnelGroup): Long

    @Query("SELECT * FROM tunnel_group ORDER BY position ASC")
    fun getAllFlow(): Flow<List<TunnelGroup>>

    @Query("SELECT * FROM tunnel_group ORDER BY position ASC")
    suspend fun getAll(): List<TunnelGroup>

    @Query("SELECT * FROM tunnel_group WHERE id = :id") suspend fun getById(id: Int): TunnelGroup?

    @Delete suspend fun delete(group: TunnelGroup)

    @Query("UPDATE tunnel_group SET expanded = :expanded WHERE id = :id")
    suspend fun setExpanded(id: Int, expanded: Boolean)
}
