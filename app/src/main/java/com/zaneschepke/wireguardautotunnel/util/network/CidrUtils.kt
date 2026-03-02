package com.zaneschepke.wireguardautotunnel.util.network

/**
 * Utility for CIDR arithmetic.
 *
 * Implements IPv4 CIDR subtraction — necessary for LAN bypass: given AllowedIPs = 0.0.0.0/0, we
 * subtract private/link-local ranges so local network traffic (Android Auto, Chromecast, NAS) is
 * NOT captured by the WireGuard TUN interface and instead routes directly over the physical
 * interface.
 *
 * Example:
 * ```
 * CidrUtils.applyLanBypass(listOf("0.0.0.0/0"), LAN_EXCLUDED_RANGES)
 * // → ["0.0.0.0/5","8.0.0.0/7","11.0.0.0/8", ... all public IPs, no 192.168.x.x etc.]
 * ```
 */
object CidrUtils {

    /** Represents a single IPv4 CIDR block. */
    data class Cidr(val addressInt: Int, val prefix: Int) {
        /** First IP of this block as an unsigned long. */
        fun firstIp(): Long = addressInt.toLong() and 0xFFFFFFFFL

        /** Last IP of this block as an unsigned long. */
        fun lastIp(): Long =
            if (prefix == 0) 0xFFFFFFFFL else firstIp() + (1L shl (32 - prefix)) - 1

        fun overlaps(other: Cidr): Boolean {
            return firstIp() <= other.lastIp() && other.firstIp() <= lastIp()
        }

        override fun toString(): String {
            val a = (addressInt ushr 24) and 0xFF
            val b = (addressInt ushr 16) and 0xFF
            val c = (addressInt ushr 8) and 0xFF
            val d = addressInt and 0xFF
            return "$a.$b.$c.$d/$prefix"
        }
    }

    /** Parse a dotted-decimal CIDR string like "192.168.0.0/16". */
    fun parse(cidr: String): Cidr {
        val slash = cidr.indexOf('/')
        val prefix = if (slash == -1) 32 else cidr.substring(slash + 1).toInt()
        val addrStr = if (slash == -1) cidr else cidr.substring(0, slash)
        val parts = addrStr.split(".")
        require(parts.size == 4) { "Invalid IPv4 address: $addrStr" }
        val addr =
            parts.fold(0) { acc, octet ->
                (acc shl 8) or octet.toInt().also { require(it in 0..255) { "Octet out of range" } }
            }
        return Cidr(addr, prefix)
    }

    /**
     * Subtracts [exclude] from [source], returning the list of CIDR blocks that cover [source] but
     * NOT [exclude].
     */
    fun subtract(source: Cidr, exclude: Cidr): List<Cidr> {
        if (!source.overlaps(exclude)) return listOf(source)
        // exclude totally encompasses source
        if (exclude.firstIp() <= source.firstIp() && exclude.lastIp() >= source.lastIp()) {
            return emptyList()
        }

        val result = mutableListOf<Cidr>()

        // IPs before the excluded block
        if (source.firstIp() < exclude.firstIp()) {
            result.addAll(rangeToCidrs(source.firstIp(), exclude.firstIp() - 1))
        }

        // IPs after the excluded block
        if (source.lastIp() > exclude.lastIp()) {
            result.addAll(rangeToCidrs(exclude.lastIp() + 1, source.lastIp()))
        }

        return result
    }

    /**
     * Converts a contiguous IP range [start, end] (inclusive, as unsigned longs) into the minimal
     * list of CIDR blocks that covers exactly that range.
     */
    fun rangeToCidrs(start: Long, end: Long): List<Cidr> {
        if (start > end) return emptyList()
        val result = mutableListOf<Cidr>()
        var cur = start

        while (cur <= end) {
            // Find the largest block that starts at cur and doesn't exceed end
            var prefix = 32
            while (prefix > 0) {
                val newPrefix = prefix - 1
                val mask = if (newPrefix == 0) 0L else ((-1L shl (32 - newPrefix)) and 0xFFFFFFFFL)
                val blockStart = cur and mask
                val blockEnd =
                    if (newPrefix == 0) 0xFFFFFFFFL
                    else blockStart + (1L shl (32 - newPrefix)) - 1
                if (blockStart == cur && blockEnd <= end) {
                    prefix = newPrefix
                } else {
                    break
                }
            }
            result.add(Cidr(cur.toInt(), prefix))
            val blockEnd =
                if (prefix == 0) 0xFFFFFFFFL
                else cur + (1L shl (32 - prefix)) - 1
            cur = blockEnd + 1
            if (cur > 0xFFFFFFFFL) break
        }

        return result
    }

    /**
     * Subtracts all [excludeRanges] CIDR strings from a single [sourceCidr] string, returning the
     * remaining non-overlapping CIDR strings.
     */
    fun subtractAll(sourceCidr: String, excludeRanges: Collection<String>): List<String> {
        var remaining = listOf(parse(sourceCidr))
        for (excl in excludeRanges) {
            val excludeCidr = parse(excl)
            remaining = remaining.flatMap { subtract(it, excludeCidr) }
        }
        return remaining.map { it.toString() }
    }

    /**
     * Given a list of IPv4 AllowedIPs CIDR strings, returns a new list with all [lanRanges]
     * excluded. IPv6 ranges (containing ':') are passed through unchanged.
     *
     * This is the main entry point for "LAN bypass" / Android Auto compatibility mode.
     *
     * @param allowedIps Original AllowedIPs (may include 0.0.0.0/0 or specific prefixes)
     * @param lanRanges  Private/link-local ranges to exclude (e.g., "192.168.0.0/16")
     * @return New AllowedIPs without the LAN ranges
     */
    fun applyLanBypass(allowedIps: List<String>, lanRanges: Collection<String>): List<String> {
        if (lanRanges.isEmpty()) return allowedIps
        return allowedIps.flatMap { cidr ->
            if (cidr.contains(':')) {
                // IPv6 — pass through as-is (handle separately if needed)
                listOf(cidr)
            } else {
                subtractAll(cidr, lanRanges)
            }
        }.distinct()
    }
}
