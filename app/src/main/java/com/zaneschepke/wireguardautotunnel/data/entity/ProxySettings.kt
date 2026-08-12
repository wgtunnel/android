package com.zaneschepke.wireguardautotunnel.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxy_settings")
data class ProxySettings(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "socks5_proxy_enabled", defaultValue = "1")
    val socks5ProxyEnabled: Boolean = true,
    @ColumnInfo(name = "socks5_proxy_bind_address") val socks5ProxyBindAddress: String? = null,
    @ColumnInfo(name = "http_proxy_enable", defaultValue = "1")
    val httpProxyEnabled: Boolean = true,
    @ColumnInfo(name = "http_proxy_bind_address") val httpProxyBindAddress: String? = null,
    @ColumnInfo(name = "proxy_username") val proxyUsername: String? = null,
    @ColumnInfo(name = "proxy_password") val proxyPassword: String? = null,
)
