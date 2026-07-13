package com.zaneschepke.tunnel.util

import com.zaneschepke.tunnel.model.DnsBootstrapResult
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig

fun ActiveConfig.findEndpointMismatches(
    freshDns: Map<PublicKey, DnsBootstrapResult>,
    preferIpv6: Boolean,
): Map<PublicKey, Host> {
    val currentEndpoints = peers.associateBy { it.publicKey }

    return freshDns
        .mapNotNull { (pubKey, dnsResult) ->
            val current = currentEndpoints[pubKey] ?: return@mapNotNull null
            val currentHost = current.host ?: return@mapNotNull null

            val freshAddress =
                if (preferIpv6 && dnsResult.ipv6.isNotEmpty()) {
                    dnsResult.ipv6.first()
                } else {
                    dnsResult.ipv4.firstOrNull() ?: dnsResult.ipv6.firstOrNull()
                } ?: return@mapNotNull null

            if (freshAddress != currentHost) {
                pubKey to freshAddress
            } else {
                null
            }
        }
        .toMap()
}
