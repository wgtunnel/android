package com.zaneschepke.tunnel.util

import android.os.Build
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.DnsBootstrapResult
import com.zaneschepke.tunnel.model.DnsConfig
import com.zaneschepke.tunnel.model.Host
import com.zaneschepke.tunnel.model.PublicKey
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.parser.Config
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

internal fun Config.buildResolvedPeers(hostMap: Map<PublicKey, Host>): List<PeerSection> {
    return this.peers.map { peer ->
        val updatedHost = hostMap[peer.publicKey] ?: return@map peer
        val port = peer.endpoint?.substringAfterLast(":") ?: return@map peer
        peer.copy(endpoint = "$updatedHost:$port")
    }
}

fun Map<PublicKey, DnsBootstrapResult>.toHostMap(preferIpv6: Boolean): Map<PublicKey, Host> =
    mapNotNull { (pubKey, result) ->
            val host =
                if (preferIpv6) {
                    result.ipv6.firstOrNull() ?: result.ipv4.firstOrNull()
                } else {
                    result.ipv4.firstOrNull() ?: result.ipv6.firstOrNull()
                }
            host?.let { pubKey to it }
        }
        .toMap()

fun BackendStatus.isLastTunnelOfServiceType(tunnelId: Int): Boolean {
    val mode = activeTunnels[tunnelId]?.mode ?: return false
    return when (mode) {
        is BackendMode.Vpn,
        is BackendMode.Proxy.KillSwitchPrimary -> {
            activeTunnels.values.count {
                it.mode is BackendMode.Vpn || it.mode is BackendMode.Proxy.KillSwitchPrimary
            } == 1
        }
        is BackendMode.Proxy.Standard -> {
            activeTunnels.values.count { it.mode is BackendMode.Proxy.Standard } == 1
        }
    }
}
