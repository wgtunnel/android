package com.zaneschepke.wireguardautotunnel.ui.state

import AllowedIpsCalculator
import com.wgtunnel.parser.PeerSection
import com.zaneschepke.wireguardautotunnel.util.extensions.joinAndTrim

data class EditablePeer(
    val publicKey: String = "",
    val preSharedKey: String = "",
    val persistentKeepalive: String = "",
    val endpoint: String = "",
    val allowedIps: String = AllowedIpsCalculator.ALL_IPS.joinAndTrim(),
) {

    fun toPeerSection(): PeerSection =
        PeerSection(
            publicKey = publicKey.trim(),
            allowedIPs = allowedIps.ifBlank { null },
            endpoint = endpoint.ifBlank { null },
            presharedKey = preSharedKey.ifBlank { null },
            // Scalar or AWG 3.0 range (e.g. 22-30)
            persistentKeepalive = persistentKeepalive.trim().ifBlank { null },
        )

    fun isLanExcluded(): Boolean {
        val current = allowedIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        return AllowedIpsCalculator.LAN_BYPASS_BASE.all { it in current }
    }

    fun includeLan(): EditablePeer =
        this.copy(allowedIps = AllowedIpsCalculator.ALL_IPS.joinAndTrim())

    // excludeLan not properly calculates LAN bypass to make sure we don't include private IP DNS
    // servers
    fun excludeLan(dnsServers: List<String>): EditablePeer =
        this.copy(allowedIps = AllowedIpsCalculator.calculateLanBypass(dnsServers).joinAndTrim())

    companion object {
        fun from(peer: PeerSection): EditablePeer =
            EditablePeer(
                publicKey = peer.publicKey,
                preSharedKey = peer.presharedKey ?: "",
                persistentKeepalive = peer.persistentKeepalive ?: "",
                endpoint = peer.endpoint ?: "",
                allowedIps = peer.allowedIPs ?: AllowedIpsCalculator.ALL_IPS.joinAndTrim(),
            )
    }
}
