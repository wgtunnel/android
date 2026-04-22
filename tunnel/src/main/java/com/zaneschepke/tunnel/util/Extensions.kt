package com.zaneschepke.tunnel.util

import android.os.Build
import com.zaneschepke.tunnel.model.DnsConfig
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Parses a CIDR string and returns the address + prefix length
 */
fun String.parseInetNetwork(): Pair<InetAddress, Int> {
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

    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

fun String.parseDns(): DnsConfig {
    val servers = mutableListOf<InetAddress>()
    val domains = mutableListOf<String>()

    split(",").forEach { item ->
        val trimmed = item.trim()
        if (trimmed.isBlank()) return@forEach

        try {
            val ip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

