package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.parser.InterfaceSection
import com.zaneschepke.wireguardautotunnel.util.extensions.toTrimmedList

data class ConfigProxy(
    val `interface`: InterfaceProxy = InterfaceProxy(),
    val peers: List<PeerProxy> = emptyList(),
    val headerComments: List<String> = emptyList(),
) {

    fun hasScripts(): Boolean = `interface`.hasScripts()

    fun buildConfig(): Config {
        val interfaceSection = InterfaceSection(
            privateKey = `interface`.privateKey.trim(),
            address = `interface`.addresses.ifBlank { null },
            dns = `interface`.dnsServers.ifBlank { null },
            listenPort = `interface`.listenPort.toIntOrNull(),
            mtu = `interface`.mtu.toIntOrNull(),
            // Scripts (UI uses single multiline string → split)
            preUp = `interface`.preUp.toTrimmedList(),
            postUp = `interface`.postUp.toTrimmedList(),
            preDown = `interface`.preDown.toTrimmedList(),
            postDown = `interface`.postDown.toTrimmedList(),
            // Android
            includedApplications = `interface`.includedApplications.toList(),
            excludedApplications = `interface`.excludedApplications.toList(),
            // Amnezia fields (mapped from old UI names)
            jC = `interface`.junkPacketCount.toIntOrNull(),
            jMin = `interface`.junkPacketMinSize.toIntOrNull(),
            jMax = `interface`.junkPacketMaxSize.toIntOrNull(),
            s1 = `interface`.initPacketJunkSize.toIntOrNull(),
            s2 = `interface`.responsePacketJunkSize.toIntOrNull(),
            s3 = `interface`.transportPacketJunkSize.toIntOrNull(),
            s4 = `interface`.cookiePacketJunkSize.toIntOrNull(),
            h1 = `interface`.initPacketMagicHeader.ifBlank { null },
            h2 = `interface`.responsePacketMagicHeader.ifBlank { null },
            h3 = `interface`.underloadPacketMagicHeader.ifBlank { null },
            h4 = `interface`.transportPacketMagicHeader.ifBlank { null },
            i1 = `interface`.i1.ifBlank { null },
            i2 = `interface`.i2.ifBlank { null },
            i3 = `interface`.i3.ifBlank { null },
            i4 = `interface`.i4.ifBlank { null },
            i5 = `interface`.i5.ifBlank { null },
        )

        val peerSections = peers.map { it.toPeerSection() }

        return Config(
            `interface` = interfaceSection,
            peers = peerSections,
            headerComments = headerComments,
        )
    }

    companion object {
        fun from(config: Config): ConfigProxy {
            return ConfigProxy(
                `interface` = InterfaceProxy.from(config.`interface`),
                peers = config.peers.map { PeerProxy.from(it) },
                headerComments = config.headerComments,
            )
        }
    }
}
