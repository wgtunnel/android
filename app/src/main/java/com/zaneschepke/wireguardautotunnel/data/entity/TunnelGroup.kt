package com.zaneschepke.wireguardautotunnel.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tunnel_group", indices = [Index(value = ["name"], unique = true)])
data class TunnelGroup(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "position", defaultValue = "0") val position: Int = 0,
    @ColumnInfo(name = "expanded", defaultValue = "1") val expanded: Boolean = true,
)
