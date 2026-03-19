package com.zaneschepke.wireguardautotunnel.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tunnel_config", indices = [Index(value = ["name"], unique = true)])
data class TunnelConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "wg_quick") val wgQuick: String,
    @ColumnInfo(name = "tunnel_networks", defaultValue = "")
    val tunnelNetworks: Set<String> = setOf(),
    @ColumnInfo(name = "is_mobile_data_tunnel", defaultValue = "false")
    val isMobileDataTunnel: Boolean = false,
    @ColumnInfo(name = "is_primary_tunnel", defaultValue = "false")
    val isPrimaryTunnel: Boolean = false,
    @ColumnInfo(name = "am_quick", defaultValue = "") val amQuick: String = "",
    @ColumnInfo(name = "is_Active", defaultValue = "false") val isActive: Boolean = false,
    @ColumnInfo(name = "restart_on_ping_failure", defaultValue = "false")
    val restartOnPingFailure: Boolean = false,
    @ColumnInfo(name = "ping_target", defaultValue = "null") var pingTarget: String? = null,
    @ColumnInfo(name = "is_ethernet_tunnel", defaultValue = "false")
    val isEthernetTunnel: Boolean = false,
    @ColumnInfo(name = "is_ipv4_preferred", defaultValue = "true")
    val isIpv4Preferred: Boolean = true,
    @ColumnInfo(name = "position", defaultValue = "0") val position: Int = 0,
    @ColumnInfo(name = "auto_tunnel_apps", defaultValue = "[]")
    val autoTunnelApps: Set<String> = emptySet(),
    @ColumnInfo(name = "is_metered", defaultValue = "false") val isMetered: Boolean = false,
    @ColumnInfo(name = "fallback_tunnel_id") val fallbackTunnelId: Int? = null,
) {
    companion object {
        const val GLOBAL_CONFIG_NAME = "4675ab06-903a-438b-8485-6ea4187a9512"
    }
}
