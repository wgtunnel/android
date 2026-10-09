package com.zaneschepke.wireguardautotunnel.ui.state

import com.wgtunnel.parser.Config
import com.wgtunnel.parser.InterfaceSection
import com.zaneschepke.wireguardautotunnel.util.extensions.toTrimmedList

data class EditableConfig(
    val `interface`: EditableInterface = EditableInterface(),
    val peers: List<EditablePeer> = emptyList(),
    val headerComments: List<String> = emptyList(),
) {

    fun buildConfig(name: String? = null): Config {
        val interfaceSection =
            InterfaceSection(
                privateKey = `interface`.privateKey.trim(),
                address = `interface`.addresses.ifBlank { null },
                dns = `interface`.dnsServers.ifBlank { null },
                listenPort = `interface`.listenPort.toIntOrNull(),
                mtu = `interface`.mtu.toIntOrNull(),
                preUp = `interface`.preUp.toTrimmedList(),
                postUp = `interface`.postUp.toTrimmedList(),
                preDown = `interface`.preDown.toTrimmedList(),
                postDown = `interface`.postDown.toTrimmedList(),
                includedApplications = `interface`.includedApplications.toList(),
                excludedApplications = `interface`.excludedApplications.toList(),
                jC = `interface`.junkPacketCount.toIntOrNull(),
                jMin = `interface`.junkPacketMinSize.toIntOrNull(),
                jMax = `interface`.junkPacketMaxSize.toIntOrNull(),
                s1 = `interface`.initPacketJunkSize.toIntOrNull(),
                s2 = `interface`.responsePacketJunkSize.toIntOrNull(),
                s3 = `interface`.cookiePacketJunkSize.toIntOrNull(),
                s4 = `interface`.transportPacketJunkSize.toIntOrNull(),
                h1 = `interface`.initPacketMagicHeader.ifBlank { null },
                h2 = `interface`.responsePacketMagicHeader.ifBlank { null },
                h3 = `interface`.underloadPacketMagicHeader.ifBlank { null },
                h4 = `interface`.transportPacketMagicHeader.ifBlank { null },
                i1 = `interface`.i1.ifBlank { null },
                i2 = `interface`.i2.ifBlank { null },
                i3 = `interface`.i3.ifBlank { null },
                i4 = `interface`.i4.ifBlank { null },
                i5 = `interface`.i5.ifBlank { null },
                headerProtectionKey = `interface`.headerProtectionKey.ifBlank { null },
                contentPaddingAddition = `interface`.contentPaddingAddition.ifBlank { null },
                rekeyAfterTime = `interface`.rekeyAfterTime.ifBlank { null },
                rekeyTimeout = `interface`.rekeyTimeout.ifBlank { null },
                rejectAfterTime = `interface`.rejectAfterTime.ifBlank { null },
                keepaliveTimeout = `interface`.keepaliveTimeout.ifBlank { null },
                maxHandshakeAttempts = `interface`.maxHandshakeAttempts.ifBlank { null },
                randomTrailers = `interface`.randomTrailers.ifBlank { null },
                disableCookies = `interface`.disableCookies.ifBlank { null },
            )

        val peerSections = peers.map { it.toPeerSection() }

        return Config(
            `interface` = interfaceSection,
            peers = peerSections,
            name = name,
            headerComments = headerComments,
        )
    }

    companion object {
        fun from(config: Config): EditableConfig {
            return EditableConfig(
                `interface` = EditableInterface.from(config.`interface`),
                peers = config.peers.map { EditablePeer.from(it) },
                headerComments = config.headerComments,
            )
        }
    }
}
