package com.zaneschepke.wireguardautotunnel.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zaneschepke.wireguardautotunnel.domain.enums.BootstrapDnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.enums.ForeignDnsPolicy
import com.zaneschepke.wireguardautotunnel.domain.enums.SplitDnsSuffixTarget
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsMode
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsProtocol

@Entity(tableName = "dns_settings")
data class DnsSettings(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "dns_boostrap_bootstrap_protocol", defaultValue = "0")
    val bootstrapDnsProtocol: BootstrapDnsProtocol = BootstrapDnsProtocol.fromValue(0),
    @ColumnInfo(name = "dns_boostrap_endpoint") val bootstrapDnsEndpoint: String? = null,
    @ColumnInfo(name = "global_tunnel_config_dns_enabled", defaultValue = "0")
    val isGlobalTunnelConfigDnsEnabled: Boolean = false,
    @ColumnInfo(name = "tunnel_dns_mode", defaultValue = "0")
    val tunnelDnsMode: TunnelDnsMode = TunnelDnsMode.Off,
    @ColumnInfo(name = "tunnel_dns_protocol", defaultValue = "doh")
    val tunnelDnsProtocol: TunnelDnsProtocol = TunnelDnsProtocol.Doh,
    @ColumnInfo(name = "use_tunnel_dns_split", defaultValue = "1")
    val useTunnelDnsServersInSplit: Boolean = true,
    @ColumnInfo(name = "tunnel_dns_endpoint") val tunnelDnsEndpoint: String? = null,
    @ColumnInfo(name = "local_suffixes") val localSuffixes: String? = null,
    @ColumnInfo(name = "foreign_dns_policy", defaultValue = "0")
    val foreignDnsPolicy: ForeignDnsPolicy = ForeignDnsPolicy.Redirect,
    // Where split-mode suffixes resolve, system (0) or tunnel (1)
    @ColumnInfo(name = "split_suffix_target", defaultValue = "0")
    val splitSuffixTarget: SplitDnsSuffixTarget = SplitDnsSuffixTarget.System,
)
