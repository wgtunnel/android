package com.zaneschepke.wireguardautotunnel.domain.model

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.wireguardautotunnel.data.entity.TunnelConfig.Companion.GLOBAL_CONFIG_NAME
import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.parser.InterfaceSection
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import com.zaneschepke.wireguardautotunnel.parser.crypto.Key
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelSummary
import com.zaneschepke.wireguardautotunnel.util.extensions.defaultName

data class TunnelConfig(
    val id: Int = 0,
    val name: String,
    val tunnelNetworks: Set<String> = setOf(),
    val isMobileDataTunnel: Boolean = false,
    val isPrimaryTunnel: Boolean = false,
    val quickConfig: String = "",
    val dynamicDnsEnabled: Boolean = false,
    val pingTarget: String? = null,
    val isEthernetTunnel: Boolean = false,
    val isIpv6Preferred: Boolean = false,
    val position: Int = 0,
    val autoTunnelApps: Set<String> = setOf(),
    val isMetered: Boolean = false,
    val ipv4FallbackEnabled: Boolean = false,
    val ipv6RestoreEnabled: Boolean = false,
) {

    fun toSummary() = TunnelSummary(id = id, name = name)

    fun getConfig(): Config {
        return Config.parseQuickString(quickConfig)
    }

    val isGlobalConfig: Boolean
        get() = name == GLOBAL_CONFIG_NAME

    fun toBackendTunnel(monitoringSettings: MonitoringSettings, scriptsEnabled: Boolean): Tunnel =
        BackendTunnel(this, monitoringSettings, scriptsEnabled)

    private class BackendTunnel(
        private val config: TunnelConfig,
        private val monitoringSettings: MonitoringSettings,
        override val scriptsEnabled: Boolean,
    ) : Tunnel {

        override val id: Int
            get() = config.id

        override val name: String
            get() = config.name

        override val isMetered: Boolean
            get() = config.isMetered

        override val ipStrategy: Tunnel.IpStrategy
            get() =
                if (config.isIpv6Preferred)
                    Tunnel.IpStrategy.PreferIpv6(
                        fallbackToIpv4Enabled = config.ipv4FallbackEnabled,
                        recoveryEnabled = config.ipv6RestoreEnabled,
                    )
                else Tunnel.IpStrategy.Ipv4Only

        override val features: Set<Tunnel.Feature>
            get() = buildSet {
                if (monitoringSettings.tunnelStatisticsEnabled) {
                    add(
                        Tunnel.Feature.ActiveConfigMonitor(
                            monitoringSettings.tunnelStatisticsPollInterval
                        )
                    )
                }

                if (config.dynamicDnsEnabled) {
                    add(Tunnel.Feature.DynamicDNS)
                }
            }

        override fun updateState(state: Tunnel.State) = Unit
    }

    companion object {

        fun tunnelConfFromQuick(amQuick: String, name: String? = null): TunnelConfig {
            val config = Config.parseQuickString(amQuick)
            return TunnelConfig(
                name = config.name ?: name ?: config.defaultName(),
                quickConfig = amQuick,
            )
        }

        fun generateDefaultGlobalConfig(): TunnelConfig {
            val privateKey: String = Key.generatePrivateKey().toBase64()
            val publicKey = Config.generatePublicKeyFromPrivateKey(privateKey)
            val config =
                Config(
                    `interface` =
                        InterfaceSection(address = "10.0.0.2/32", privateKey = privateKey),
                    peers =
                        listOf(
                            PeerSection(
                                publicKey = publicKey,
                                endpoint = "server.example.com:51820",
                                allowedIPs = "0.0.0.0/0",
                            )
                        ),
                )
            return TunnelConfig(name = GLOBAL_CONFIG_NAME, quickConfig = config.asQuickString())
        }

        private const val IPV6_ALL_NETWORKS = "::/0"
        private const val IPV4_ALL_NETWORKS = "0.0.0.0/0"
        val ALL_IPS = listOf(IPV4_ALL_NETWORKS, IPV6_ALL_NETWORKS)
        val IPV4_PUBLIC_NETWORKS =
            setOf(
                "0.0.0.0/5",
                "8.0.0.0/7",
                "11.0.0.0/8",
                "12.0.0.0/6",
                "16.0.0.0/4",
                "32.0.0.0/3",
                "64.0.0.0/2",
                "128.0.0.0/3",
                "160.0.0.0/5",
                "168.0.0.0/6",
                "172.0.0.0/12",
                "172.32.0.0/11",
                "172.64.0.0/10",
                "172.128.0.0/9",
                "173.0.0.0/8",
                "174.0.0.0/7",
                "176.0.0.0/4",
                "192.0.0.0/9",
                "192.128.0.0/11",
                "192.160.0.0/13",
                "192.169.0.0/16",
                "192.170.0.0/15",
                "192.172.0.0/14",
                "192.176.0.0/12",
                "192.192.0.0/10",
                "193.0.0.0/8",
                "194.0.0.0/7",
                "196.0.0.0/6",
                "200.0.0.0/5",
                "208.0.0.0/4",
            )
        val LAN_BYPASS_ALLOWED_IPS = setOf(IPV6_ALL_NETWORKS) + IPV4_PUBLIC_NETWORKS
    }
}
