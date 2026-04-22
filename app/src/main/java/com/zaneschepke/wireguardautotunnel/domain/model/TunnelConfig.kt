package com.zaneschepke.wireguardautotunnel.domain.model

import com.zaneschepke.wireguardautotunnel.data.entity.TunnelConfig.Companion.GLOBAL_CONFIG_NAME
import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.util.extensions.defaultName

data class TunnelConfig(
    val id: Int = 0,
    val name: String,
    val tunnelNetworks: Set<String> = setOf(),
    val isMobileDataTunnel: Boolean = false,
    val isPrimaryTunnel: Boolean = false,
    val quickConfig: String = "",
    val isActive: Boolean = false,
    val restartOnPingFailure: Boolean = false,
    val pingTarget: String? = null,
    val isEthernetTunnel: Boolean = false,
    val isIpv4Preferred: Boolean = true,
    val position: Int = 0,
    val autoTunnelApps: Set<String> = setOf(),
    val isMetered: Boolean = false,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TunnelConfig) return false
        return id == other.id &&
            name == other.name &&
            quickConfig == other.quickConfig &&
            isPrimaryTunnel == other.isPrimaryTunnel &&
            isMobileDataTunnel == other.isMobileDataTunnel &&
            isEthernetTunnel == other.isEthernetTunnel &&
            pingTarget == other.pingTarget &&
            restartOnPingFailure == other.restartOnPingFailure &&
            tunnelNetworks == other.tunnelNetworks &&
            isIpv4Preferred == other.isIpv4Preferred &&
            isMetered == other.isMetered
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + quickConfig.hashCode()
        return result
    }

    fun getConfig() : Config {
        return Config.parseQuickString(quickConfig)
    }

    companion object {

        fun tunnelConfFromQuick(amQuick: String, name: String? = null): TunnelConfig {
            val config = Config.parseQuickString(amQuick)
            return TunnelConfig(
                name = name ?: config.defaultName(),
                quickConfig = amQuick,
            )
        }

        // TODO
        fun generateDefaultGlobalConfig(): TunnelConfig {
//            val keyPair = KeyPair()
//            val config =
//                org.amnezia.awg.config.Config.Builder()
//                    .apply {
//                        setInterface(
//                            Interface.Builder()
//                                .apply {
//                                    setKeyPair(keyPair)
//                                    parseAddresses("10.0.0.2/32")
//                                }
//                                .build()
//                        )
//                        addPeer(
//                            Peer.Builder()
//                                .apply {
//                                    setPublicKey(keyPair.publicKey)
//                                    addAllowedIps(listOf(InetNetwork.parse("0.0.0.0/0")))
//                                    setEndpoint(InetEndpoint.parse("server.example.com:51820"))
//                                }
//                                .build()
//                        )
//                    }
//                    .build()
            return TunnelConfig(
                name = GLOBAL_CONFIG_NAME,
                quickConfig = "",
            )
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
