package com.zaneschepke.tunnel.util

import inet.ipaddr.IPAddressString
import java.net.URI

object DnsHostUtils {

    /** Extracts the host portion from a DoH/DoT/Plain upstream string. */
    fun extractHost(upstream: String): String {
        val trimmed = upstream.trim()

        // DoH full url
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return try {
                URI(trimmed).host ?: trimmed
            } catch (_: Exception) {
                trimmed
            }
        }

        val hostPart = trimmed.substringBeforeLast(":")
        return hostPart.removeSurrounding("[", "]")
    }

    /** Replaces the hostname in the upstream string with the given IP address. */
    fun replaceHostWithIP(upstream: String, newIp: String): String {
        val trimmed = upstream.trim()

        val cleanedIp = newIp.trim().removeSurrounding("[", "]")
        val isIpv6 = isIpAddress(cleanedIp) && cleanedIp.contains(":")

        val replacementIp = if (isIpv6) "[$cleanedIp]" else cleanedIp

        // handle full url for DoH
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return try {
                val uri = URI(trimmed)
                val newAuthority =
                    if (uri.port != -1) {
                        "$replacementIp:${uri.port}"
                    } else {
                        replacementIp
                    }

                URI(uri.scheme, newAuthority, uri.path, uri.query, uri.fragment).toString()
            } catch (_: Exception) {
                // ust return the IP if URL parsing fails
                replacementIp
            }
        }

        // host:port format DoT and plain
        if (trimmed.contains(":")) {
            val port = trimmed.substringAfterLast(":")
            // Only treat as port if it's numeric
            if (port.toIntOrNull() != null) {
                return "$replacementIp:$port"
            }
        }

        // bare hostname/ip
        return replacementIp
    }

    fun isIpAddress(host: String): Boolean {
        val cleaned = host.trim().removeSurrounding("[", "]")
        return try {
            val addr = IPAddressString(cleaned).address
            addr != null && (addr.isIPv4 || addr.isIPv6)
        } catch (_: Exception) {
            false
        }
    }

    fun needsResolution(upstream: String): Boolean {
        val host = extractHost(upstream)
        return host.isNotBlank() && !isIpAddress(host)
    }
}
