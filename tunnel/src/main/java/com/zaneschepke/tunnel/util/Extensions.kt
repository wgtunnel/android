package com.zaneschepke.tunnel.util

import android.os.Build
import com.zaneschepke.tunnel.model.DnsBootstrapResult
import com.zaneschepke.tunnel.model.DnsConfig
import com.zaneschepke.tunnel.model.RunningTunnel
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import java.net.Inet4Address
import java.net.InetAddress

/** Parses a CIDR string and returns the address + prefix length */
internal fun String.parseInetNetwork(): Pair<InetAddress, Int> {
    val slashIndex = lastIndexOf('/')
    val rawAddress: String
    val rawMask: String?

    if (slashIndex >= 0) {
        rawAddress = substring(0, slashIndex).trim()
        rawMask = substring(slashIndex + 1).trim()
    } else {
        rawAddress = trim()
        rawMask = null
    }

    val address =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.net.InetAddresses.parseNumericAddress(rawAddress)
        } else {
            InetAddress.getByName(rawAddress)
        }

    val maxMask = if (address is Inet4Address) 32 else 128
    val mask = rawMask?.toIntOrNull() ?: maxMask

    if (mask !in 0..maxMask) {
        throw IllegalArgumentException("Invalid network mask: $rawMask (must be 0-$maxMask)")
    }

    return address to mask
}

internal fun String.parseDns(): DnsConfig {
    val servers = mutableListOf<InetAddress>()
    val domains = mutableListOf<String>()

    split(",").forEach { item ->
        val trimmed = item.trim()
        if (trimmed.isBlank()) return@forEach

        try {
            val ip =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    android.net.InetAddresses.parseNumericAddress(trimmed)
                } else {
                    InetAddress.getByName(trimmed)
                }
            servers.add(ip)
        } catch (_: Exception) {
            domains.add(trimmed)
        }
    }

    return DnsConfig(servers, domains)
}

internal fun RunningTunnel.buildResolvedPeers(preferIpv6: Boolean): List<PeerSection> {

    fun selectIp(cache: DnsBootstrapResult, preferIpv6: Boolean): String? {

        val ipv4 = cache.ipv4.firstOrNull()
        val ipv6 = cache.ipv6.firstOrNull()

        return when {
            preferIpv6 -> ipv6 ?: ipv4
            else -> ipv4 ?: ipv6
        }
    }

    return mode.config.peers.map { peer ->
        val endpoint = peer.endpoint ?: return@map peer
        val port = endpoint.substringAfterLast(":")

        val dnsCache = peerBootstrapCache[peer.publicKey] ?: return@map peer

        val selectedIp = selectIp(cache = dnsCache, preferIpv6 = preferIpv6) ?: return@map peer

        peer.copy(endpoint = "$selectedIp:$port")
    }
}
