package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.parser.InterfaceSection
import com.zaneschepke.wireguardautotunnel.util.extensions.joinAndTrim

data class EditableInterface(
    val privateKey: String = "",
    val publicKey: String = "",
    val addresses: String = "",
    val dnsServers: String = "",
    val listenPort: String = "",
    val mtu: String = "",
    val includedApplications: Set<String> = emptySet(),
    val excludedApplications: Set<String> = emptySet(),
    val preUp: String = "",
    val postUp: String = "",
    val preDown: String = "",
    val postDown: String = "",
    val junkPacketCount: String = "",
    val junkPacketMinSize: String = "",
    val junkPacketMaxSize: String = "",
    val cookiePacketJunkSize: String = "",
    val transportPacketJunkSize: String = "",
    val initPacketJunkSize: String = "",
    val responsePacketJunkSize: String = "",
    val initPacketMagicHeader: String = "",
    val responsePacketMagicHeader: String = "",
    val underloadPacketMagicHeader: String = "",
    val transportPacketMagicHeader: String = "",
    val i1: String = "",
    val i2: String = "",
    val i3: String = "",
    val i4: String = "",
    val i5: String = "",
) {

    fun hasScripts(): Boolean = listOf(preUp, postUp, preDown, postDown).any { it.isNotBlank() }

    fun isAmneziaEnabled(): Boolean {
        return listOf(
                junkPacketCount,
                junkPacketMinSize,
                junkPacketMaxSize,
                initPacketJunkSize,
                transportPacketJunkSize,
                cookiePacketJunkSize,
                responsePacketJunkSize,
                initPacketMagicHeader,
                responsePacketMagicHeader,
                underloadPacketMagicHeader,
                transportPacketMagicHeader,
                i1,
                i2,
                i3,
                i4,
                i5,
            )
            .any { it.isNotBlank() }
    }

    fun applyAmneziaDefaults(): EditableInterface {
        return copy(
            junkPacketCount = "4",
            junkPacketMinSize = "40",
            junkPacketMaxSize = "70",
            initPacketJunkSize = "0",
            responsePacketJunkSize = "0",
            transportPacketJunkSize = "0",
            cookiePacketJunkSize = "0",
            initPacketMagicHeader = "1",
            responsePacketMagicHeader = "2",
            underloadPacketMagicHeader = "3",
            transportPacketMagicHeader = "4",
        )
    }

    fun resetAmneziaProperties(): EditableInterface {
        return copy(
            junkPacketCount = "",
            junkPacketMinSize = "",
            junkPacketMaxSize = "",
            initPacketJunkSize = "",
            responsePacketJunkSize = "",
            transportPacketJunkSize = "",
            cookiePacketJunkSize = "",
            initPacketMagicHeader = "",
            responsePacketMagicHeader = "",
            underloadPacketMagicHeader = "",
            transportPacketMagicHeader = "",
            i1 = "",
            i2 = "",
            i3 = "",
            i4 = "",
            i5 = "",
        )
    }

    fun isAmneziaCompatibilityModeSet(): Boolean {
        return (initPacketJunkSize.toIntOrNull() ?: 0) == 0 &&
            (responsePacketJunkSize.toIntOrNull() ?: 0) == 0 &&
            initPacketMagicHeader.toLongOrNull() == 1L &&
            responsePacketMagicHeader.toLongOrNull() == 2L &&
            underloadPacketMagicHeader.toLongOrNull() == 3L &&
            transportPacketMagicHeader.toLongOrNull() == 4L
    }

    fun ensureAmneziaBaseParameters(): EditableInterface {
        return copy(
            junkPacketCount = junkPacketCount.ifBlank { "4" },
            junkPacketMinSize = junkPacketMinSize.ifBlank { "40" },
            junkPacketMaxSize = junkPacketMaxSize.ifBlank { "70" },
            initPacketJunkSize = initPacketJunkSize.ifBlank { "0" },
            responsePacketJunkSize = responsePacketJunkSize.ifBlank { "0" },
            transportPacketJunkSize = transportPacketJunkSize.ifBlank { "0" },
            cookiePacketJunkSize = cookiePacketJunkSize.ifBlank { "0" },
            initPacketMagicHeader = initPacketMagicHeader.ifBlank { "1" },
            responsePacketMagicHeader = responsePacketMagicHeader.ifBlank { "2" },
            underloadPacketMagicHeader = underloadPacketMagicHeader.ifBlank { "3" },
            transportPacketMagicHeader = transportPacketMagicHeader.ifBlank { "4" },
        )
    }

    /**
     * Mimics QUIC (HTTP/3) traffic by setting i1 to a QUIC initial packet and i2 to a follow-up
     * frame. Adds j1 for extra obfuscation. Compatible with standard WireGuard servers.
     */
    fun setQuicMimic(): EditableInterface {
        return ensureAmneziaBaseParameters()
            .copy(
                i1 =
                    "<b 0xc1ff000012508394c8f03e51570800449f0dbc195a0000f3a694c75775b4e546172ce9e047cd0b5bee5181648c727adc87f7eae54473ec6cba6bdad4f59823174b769f12358abd292d4f3286934484fb8b239c38732e1f3bbbc6a003056487eb8b5c88b9fd9279ffff3b0f4ecf95c4624db6d65d4113329ee9b0bf8cdd7c8a8d72806d55df25ecb66488bc119d7c9a29abaf99bb33c56b08ad8c26995f838bb3b7a3d5c1858b8ec06b839db2dcf918d5ea9317f1acd6b663cc8925868e2f6a1bda546695f3c3f33175944db4a11a346afb07e78489e509b02add51b7b203eda5c330b03641179a31fbba9b56ce00f3d5b5e3d7d9c5429aebb9576f2f7eacbe27bc1b8082aaf68fb69c921aa5d33ec0c8510410865a178d86d7e54122d55ef2c2bbc040be46d7fece73fe8a1b24495ec160df2da9b20a7ba2f26dfa2a44366dbc63de5cd7d7c94c57172fe6d79c901f025c0010b02c89b395402c009f62dc053b8067a1e0ed0a1e0cf5087d7f78cbd94afe0c3dd55d2d4b1a5cfe2b68b86264e351d1dcd858783a240f893f008ceed743d969b8f735a1677ead960b1fb1ecc5ac83c273b49288d02d7286207e663c45e1a7baf50640c91e762941cf380ce8d79f3e86767fbbcd25b42ef70ec334835a3a6d792e170a432ce0cb7bde9aaa1e75637c1c34ae5fef4338f53db8b13a4d2df594efbfa08784543815c9c0d487bddfa1539bc252cf43ec3686e9802d651cfd2a829a06a9f332a733a4a8aed80efe3478093fbc69c8608146b3f16f1a5c4eac9320da49f1afa5f538ddecbbe7888f435512d0dd74fd9b8c99e3145ba84410d8ca9a36dd884109e76e5fb8222a52e1473da168519ce7a8a3c32e9149671b16724c6c5c51bb5cd64fb591e567fb78b10f9f6fee62c276f282a7df6bcf7c17747bc9a81e6c9c3b032fdd0e1c3ac9eaa5077de3ded18b2ed4faf328f49875af2e36ad5ce5f6cc99ef4b60e57b3b5b9c9fcbcd4cfb3975e70ce4c2506bcd71fef0e53592461504e3d42c885caab21b782e26294c6a9d61118cc40a26f378441ceb48f31a362bf8502a723a36c63502229a462cc2a3796279a5e3a7f81a68c7f81312c381cc16a4ab03513a51ad5b54306ec1d78a5e47e2b15e5b7a1438e5b8b2882dbdad13d6a4a8c3558cae043501b68eb3b040067152>",
                i2 = "<b 0x0000000000010000000000000000000000000000000000000000000000000000>",
            )
    }

    /**
     * Mimics DNS query traffic with a single i1 packet. DNS is typically single-packet, so no
     * additional i/j packets are set. Compatible with standard WireGuard servers.
     */
    fun setDnsMimic(): EditableInterface {
        return ensureAmneziaBaseParameters()
            .copy(i1 = "<b 0x123401000001000000000000076578616d706c6503636f6d0000010001>", i2 = "")
    }

    /**
     * Mimics SIP (VoIP) traffic with i1 as an INVITE packet and i2 as a follow-up (e.g., TRYING
     * response). Adds j1 for extra obfuscation. Compatible with standard WireGuard servers.
     */
    fun setSipMimic(): EditableInterface {
        return ensureAmneziaBaseParameters()
            .copy(
                i1 =
                    "<b 0x494e56495445207369703a626f624062696c6f78692e636f6d205349502f322e300d0a5669613a205349502f322e302f55445020706333332e61746c616e74612e636f6d3b6272616e63683d7a39684734624b3737366173646864730d0a4d61782d466f7277617264733a2037300d0a546f3a20426f62203c7369703a626f624062696c6f78692e636f6d3e0d0a46726f6d3a20416c696365203c7369703a616c6963654061746c616e74612e636f6d3e3b7461673d313932383330313737340d0a43616c6c2d49443a20613834623463373665363637313040706333332e61746c616e74612e636f6d0d0a435365713a2033313431353920494e564954450d0a436f6e746163743a203c7369703a616c69636540706333332e61746c616e74612e636f6d3e0d0a436f6e74656e742d547970653a206170706c69636174696f6e2f7364700d0a436f6e74656e742d4c656e6774683a20300d0a0d0a>",
                i2 =
                    "<b 0x5349502f322e302031303020547279696e670d0a5669613a205349502f322e302f55445020706333332e61746c616e74612e636f6d3b6272616e63683d7a39684734624b3737366173646864730d0a546f3a20426f62203c7369703a626f624062696c6f78692e636f6d3e0d0a46726f6d3a20416c696365203c7369703a616c6963654061746c616e74612e636f6d3e3b7461673d313932383330313737340d0a43616c6c2d49443a20613834623463373665363637313040706333332e61746c616e74612e636f6d0d0a435365713a2033313431353920494e564954450d0a436f6e74656e742d4c656e6774683a20300d0a0d0a>",
            )
    }

    companion object {
        fun from(i: InterfaceSection): EditableInterface {
            val pubKey =
                if (i.privateKey.isNotBlank()) {
                    try {
                        Config.generatePublicKeyFromPrivateKey(i.privateKey)
                    } catch (e: Exception) {
                        ""
                    }
                } else ""

            return EditableInterface(
                privateKey = i.privateKey,
                publicKey = pubKey,
                addresses = i.address?.trim() ?: "",
                dnsServers = i.dns?.trim() ?: "",
                listenPort = i.listenPort?.toString() ?: "",
                mtu = i.mtu?.toString() ?: "",
                includedApplications = i.includedApplications.orEmpty().toSet(),
                excludedApplications = i.excludedApplications.orEmpty().toSet(),
                preUp = i.preUp?.joinAndTrim() ?: "",
                postUp = i.postUp?.joinAndTrim() ?: "",
                preDown = i.preDown?.joinAndTrim() ?: "",
                postDown = i.postDown?.joinAndTrim() ?: "",
                junkPacketCount = i.jC?.toString() ?: "",
                junkPacketMinSize = i.jMin?.toString() ?: "",
                junkPacketMaxSize = i.jMax?.toString() ?: "",
                initPacketJunkSize = i.s1?.toString() ?: "",
                responsePacketJunkSize = i.s2?.toString() ?: "",
                transportPacketJunkSize = i.s3?.toString() ?: "",
                cookiePacketJunkSize = i.s4?.toString() ?: "",
                initPacketMagicHeader = i.h1 ?: "",
                responsePacketMagicHeader = i.h2 ?: "",
                underloadPacketMagicHeader = i.h3 ?: "",
                transportPacketMagicHeader = i.h4 ?: "",
                i1 = i.i1 ?: "",
                i2 = i.i2 ?: "",
                i3 = i.i3 ?: "",
                i4 = i.i4 ?: "",
                i5 = i.i5 ?: "",
            )
        }
    }
}
