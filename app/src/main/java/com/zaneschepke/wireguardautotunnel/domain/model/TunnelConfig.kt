package com.zaneschepke.wireguardautotunnel.domain.model

import com.wireguard.config.Config
import com.zaneschepke.wireguardautotunnel.data.entity.TunnelConfig.Companion.GLOBAL_CONFIG_NAME
import com.zaneschepke.wireguardautotunnel.util.extensions.defaultName
import com.zaneschepke.wireguardautotunnel.util.extensions.isValidIpv4orIpv6Address
import java.io.InputStream
import java.nio.charset.StandardCharsets
import org.amnezia.awg.config.InetEndpoint
import org.amnezia.awg.config.InetNetwork
import org.amnezia.awg.config.Interface
import org.amnezia.awg.config.Peer
import org.amnezia.awg.crypto.KeyPair

data class TunnelConfig(
    val id: Int = 0,
    val name: String,
    val wgQuick: String,
    val tunnelNetworks: Set<String> = setOf(),
    val isMobileDataTunnel: Boolean = false,
    val isPrimaryTunnel: Boolean = false,
    val amQuick: String = "",
    val isActive: Boolean = false,
    val restartOnPingFailure: Boolean = false,
    var pingTarget: String? = null,
    val isEthernetTunnel: Boolean = false,
    val isIpv4Preferred: Boolean = true,
    val position: Int = 0,
    val autoTunnelApps: Set<String> = setOf(),
    val isMetered: Boolean = false,
    val fallbackTunnelId: Int? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TunnelConfig) return false
        return id == other.id &&
            name == other.name &&
            wgQuick == other.wgQuick &&
            amQuick == other.amQuick &&
            isPrimaryTunnel == other.isPrimaryTunnel &&
            isMobileDataTunnel == other.isMobileDataTunnel &&
            isEthernetTunnel == other.isEthernetTunnel &&
            pingTarget == other.pingTarget &&
            restartOnPingFailure == other.restartOnPingFailure &&
            tunnelNetworks == other.tunnelNetworks &&
            isIpv4Preferred == other.isIpv4Preferred &&
            isMetered == other.isMetered &&
            fallbackTunnelId == other.fallbackTunnelId
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + wgQuick.hashCode()
        result = 31 * result + amQuick.hashCode()
        result = 31 * result + (fallbackTunnelId ?: 0)
        return result
    }

    fun isStaticallyConfigured(): Boolean {
        return toAmConfig().peers.all { it.endpoint.get().host.isValidIpv4orIpv6Address() }
    }

    fun toAmConfig(): org.amnezia.awg.config.Config {
        return configFromAmQuick(amQuick.ifBlank { wgQuick })
    }

    fun toWgConfig(): Config {
        return configFromWgQuick(wgQuick)
    }

    fun copyWithGlobalValues(
        globalTunnel: TunnelConfig,
        includeDns: Boolean,
        includeSpitTunneling: Boolean,
    ): TunnelConfig {
        val existingConfig = toAmConfig()
        val globalConfig = globalTunnel.toAmConfig()

        val newInterfaceBuilder =
            Interface.Builder().apply {
                setKeyPair(existingConfig.`interface`.keyPair)
                setAddresses(existingConfig.`interface`.addresses)
                setDnsServers(existingConfig.`interface`.dnsServers)
                setDnsSearchDomains(existingConfig.`interface`.dnsSearchDomains)
                setExcludedApplications(existingConfig.`interface`.excludedApplications)
                setIncludedApplications(existingConfig.`interface`.includedApplications)
                existingConfig.`interface`.listenPort.ifPresent { setListenPort(it) }
                existingConfig.`interface`.mtu.ifPresent { setMtu(it) }
                existingConfig.`interface`.junkPacketCount.ifPresent { setJunkPacketCount(it) }
                existingConfig.`interface`.junkPacketMinSize.ifPresent { setJunkPacketMinSize(it) }
                existingConfig.`interface`.junkPacketMaxSize.ifPresent { setJunkPacketMaxSize(it) }
                existingConfig.`interface`.initPacketJunkSize.ifPresent {
                    setInitPacketJunkSize(it)
                }
                existingConfig.`interface`.responsePacketJunkSize.ifPresent {
                    setResponsePacketJunkSize(it)
                }
                existingConfig.`interface`.initPacketMagicHeader.ifPresent {
                    setInitPacketMagicHeader(it)
                }
                existingConfig.`interface`.responsePacketMagicHeader.ifPresent {
                    setResponsePacketMagicHeader(it)
                }
                existingConfig.`interface`.underloadPacketMagicHeader.ifPresent {
                    setUnderloadPacketMagicHeader(it)
                }
                existingConfig.`interface`.transportPacketMagicHeader.ifPresent {
                    setTransportPacketMagicHeader(it)
                }
                existingConfig.`interface`.cookieReplyPacketJunkSize.ifPresent {
                    setCookieReplyPacketJunkSize(it)
                }
                existingConfig.`interface`.transportPacketJunkSize.ifPresent {
                    setTransportPacketJunkSize(it)
                }
                existingConfig.`interface`.specialJunkI1.ifPresent { setSpecialJunkI1(it) }
                existingConfig.`interface`.specialJunkI2.ifPresent { setSpecialJunkI2(it) }
                existingConfig.`interface`.specialJunkI3.ifPresent { setSpecialJunkI3(it) }
                existingConfig.`interface`.specialJunkI4.ifPresent { setSpecialJunkI4(it) }
                existingConfig.`interface`.specialJunkI5.ifPresent { setSpecialJunkI5(it) }
                setPreUp(existingConfig.`interface`.preUp)
                setPostUp(existingConfig.`interface`.postUp)
                setPreDown(existingConfig.`interface`.preDown)
                setPostDown(existingConfig.`interface`.postDown)

                if (includeDns) {
                    setDnsServers(globalConfig.`interface`.dnsServers)
                    setDnsSearchDomains(globalConfig.`interface`.dnsSearchDomains)
                }
                if (includeSpitTunneling) {
                    setExcludedApplications(globalConfig.`interface`.excludedApplications)
                    setIncludedApplications(globalConfig.`interface`.includedApplications)
                }
            }
        val newInterface = newInterfaceBuilder.build()

        val newConfigBuilder =
            org.amnezia.awg.config.Config.Builder().apply {
                setInterface(newInterface)
                addPeers(existingConfig.peers)
            }
        val newAmConfig = newConfigBuilder.build()

        return copy(
            wgQuick = newAmConfig.toWgQuickString(true),
            amQuick = newAmConfig.toAwgQuickString(true, false),
        )
    }

    companion object {
        fun configFromWgQuick(wgQuick: String): Config {
            val inputStream: InputStream = wgQuick.byteInputStream()
            return inputStream.bufferedReader(StandardCharsets.UTF_8).use { Config.parse(it) }
        }

        fun configFromAmQuick(amQuick: String): org.amnezia.awg.config.Config {
            val inputStream: InputStream = amQuick.byteInputStream()
            return inputStream.bufferedReader(StandardCharsets.UTF_8).use {
                org.amnezia.awg.config.Config.parse(it)
            }
        }

        fun tunnelConfFromQuick(amQuick: String, name: String? = null): TunnelConfig {
            val config = configFromAmQuick(amQuick)
            val wgQuick = config.toWgQuickString(true)
            return TunnelConfig(
                name = name ?: config.defaultName(),
                wgQuick = wgQuick,
                amQuick = amQuick,
            )
        }

        private fun tunnelConfFromAmConfig(
            config: org.amnezia.awg.config.Config,
            name: String? = null,
        ): TunnelConfig {
            val amQuick = config.toAwgQuickString(true, false)
            val wgQuick = config.toWgQuickString(true)
            return TunnelConfig(
                name = name ?: config.defaultName(),
                wgQuick = wgQuick,
                amQuick = amQuick,
            )
        }

        fun generateDefaultGlobalConfig(): TunnelConfig {
            val keyPair = KeyPair()
            val config =
                org.amnezia.awg.config.Config.Builder()
                    .apply {
                        setInterface(
                            Interface.Builder()
                                .apply {
                                    setKeyPair(keyPair)
                                    parseAddresses("10.0.0.2/32")
                                }
                                .build()
                        )
                        addPeer(
                            Peer.Builder()
                                .apply {
                                    setPublicKey(keyPair.publicKey)
                                    addAllowedIps(listOf(InetNetwork.parse("0.0.0.0/0")))
                                    setEndpoint(InetEndpoint.parse("server.example.com:51820"))
                                }
                                .build()
                        )
                    }
                    .build()
            return TunnelConfig(
                name = GLOBAL_CONFIG_NAME,
                amQuick = config.toAwgQuickString(false, false),
                wgQuick = config.toWgQuickString(false),
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
