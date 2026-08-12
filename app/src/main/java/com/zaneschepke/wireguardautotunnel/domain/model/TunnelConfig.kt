package com.zaneschepke.wireguardautotunnel.domain.model

import com.wgtunnel.backend.Tunnel
import com.wgtunnel.parser.Config
import com.wgtunnel.parser.InterfaceSection
import com.wgtunnel.parser.PeerSection
import com.wgtunnel.parser.crypto.Key
import com.zaneschepke.wireguardautotunnel.data.entity.TunnelConfig.Companion.GLOBAL_CONFIG_NAME
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelSummary
import com.zaneschepke.wireguardautotunnel.util.extensions.defaultName

data class TunnelConfig(
    val id: Int = 0,
    val name: String,
    val tunnelNetworks: List<String> = emptyList(),
    val isMobileDataTunnel: Boolean = false,
    val isPrimaryTunnel: Boolean = false,
    val quickConfig: String = "",
    val isEthernetTunnel: Boolean = false,
    val isIpv6Preferred: Boolean = false,
    val position: Int = 0,
    val autoTunnelApps: List<String> = listOf(),
    val isMetered: Boolean = false,
    val ipv6RestoreEnabled: Boolean = false,
    val tunnelBSSIDs: List<String> = emptyList(),
    val isDDNSTunnel: Boolean = false,
) {

    fun toSummary() = TunnelSummary(id = id, name = name)

    fun getConfig(): Config {
        return Config.parseQuickString(quickConfig)
    }

    val isGlobalConfig: Boolean
        get() = name == GLOBAL_CONFIG_NAME

    fun toBackendTunnel(
        monitoringSettings: MonitoringSettings,
        scriptsEnabled: Boolean,
        generalSettings: GeneralSettings,
    ): Tunnel = BackendTunnel(this, generalSettings, monitoringSettings, scriptsEnabled)

    private class BackendTunnel(
        private val config: TunnelConfig,
        private val generalSettings: GeneralSettings,
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
                    Tunnel.IpStrategy.PreferIpv6(recoveryEnabled = config.ipv6RestoreEnabled)
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
                add(
                    Tunnel.Feature.Recovery(
                        seamlessRecovery = generalSettings.seamlessRecoveryEnabled,
                        dynamicDnsRecovery = config.isDDNSTunnel,
                        ipv4Fallback = config.isIpv6Preferred,
                        ipv6Recovery = config.ipv6RestoreEnabled,
                    )
                )
            }

        override fun updateState(state: Tunnel.State) = Unit
    }

    companion object {

        fun fromConfig(config: Config, nameIfMissing: String? = null): TunnelConfig {
            return TunnelConfig(
                name = config.name ?: nameIfMissing ?: config.defaultName(),
                quickConfig = config.asQuickString(),
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
    }
}
