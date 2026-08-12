package com.zaneschepke.wireguardautotunnel.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tunnel_config", indices = [Index(value = ["name"], unique = true)])
data class TunnelConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "tunnel_networks", defaultValue = "[]")
    val tunnelNetworks: List<String> = emptyList(),
    @ColumnInfo(name = "is_mobile_data_tunnel", defaultValue = "0")
    val isMobileDataTunnel: Boolean = false,
    @ColumnInfo(name = "is_primary_tunnel", defaultValue = "0")
    val isPrimaryTunnel: Boolean = false,
    @ColumnInfo(name = "quick_config", defaultValue = "") val quickConfig: String = "",
    @ColumnInfo(name = "is_ethernet_tunnel", defaultValue = "0")
    val isEthernetTunnel: Boolean = false,
    @ColumnInfo(name = "prefer_ipv6", defaultValue = "0") val isIpv6Preferred: Boolean = false,
    @ColumnInfo(name = "position", defaultValue = "0") val position: Int = 0,
    @ColumnInfo(name = "auto_tunnel_apps", defaultValue = "[]")
    val autoTunnelApps: List<String> = emptyList(),
    @ColumnInfo(name = "is_metered", defaultValue = "0") val isMetered: Boolean = false,
    @ColumnInfo(name = "ipv6_restore", defaultValue = "0") val ipv6RestoreEnabled: Boolean = false,
    @ColumnInfo(name = "tunnel_bssids", defaultValue = "[]")
    val tunnelBSSIDs: List<String> = emptyList(),
    @ColumnInfo(name = "is_ddns_tunnel", defaultValue = "0") val isDDNSTunnel: Boolean = false,
) {
    companion object {
        const val GLOBAL_CONFIG_NAME = "4675ab06-903a-438b-8485-6ea4187a9512"
    }
}
