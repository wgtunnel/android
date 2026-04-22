package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import com.zaneschepke.wireguardautotunnel.util.extensions.joinAndTrim

data class PeerProxy(
    val publicKey: String = "",
    val preSharedKey: String = "",
    val persistentKeepalive: String = "",
    val endpoint: String = "",
    val allowedIps: String = TunnelConfig.ALL_IPS.joinAndTrim(),
) {

    fun toPeerSection(): PeerSection =
        PeerSection(
            publicKey = publicKey.trim(),
            allowedIPs = allowedIps.ifBlank { null },
            endpoint = endpoint.ifBlank { null },
            presharedKey = preSharedKey.ifBlank { null },
            persistentKeepalive = persistentKeepalive.toIntOrNull(),
        )

    fun isLanExcluded(): Boolean =
        this.allowedIps.contains(TunnelConfig.LAN_BYPASS_ALLOWED_IPS.joinAndTrim())

    fun includeLan(): PeerProxy =
        this.copy(allowedIps = TunnelConfig.ALL_IPS.joinAndTrim())

    fun excludeLan(): PeerProxy =
        this.copy(allowedIps = TunnelConfig.LAN_BYPASS_ALLOWED_IPS.joinAndTrim())

    companion object {
        fun from(peer: PeerSection): PeerProxy =
            PeerProxy(
                publicKey = peer.publicKey,
                preSharedKey = peer.presharedKey ?: "",
                persistentKeepalive = peer.persistentKeepalive?.toString() ?: "",
                endpoint = peer.endpoint ?: "",
                allowedIps = peer.allowedIPs ?: TunnelConfig.ALL_IPS.joinAndTrim(),
            )
    }
}