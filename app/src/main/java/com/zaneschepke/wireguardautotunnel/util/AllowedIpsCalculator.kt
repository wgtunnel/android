import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString

object AllowedIpsCalculator {
    private const val IPV6_PUBLIC =
        "2000::/3" // Global unicast to excluded ULA and link-local IPv6 address
    private const val IPV6_ALL_NETWORKS = "::/0"
    private const val IPV4_ALL_NETWORKS = "0.0.0.0/0"

    // Official WireGuard Android IPv4 public ranges
    private val IPV4_PUBLIC =
        setOf(
            "0.0.0.0/5",
            "8.0.0.0/7",
            "11.0.0.0/8",
            "12.0.0.0/6",
            "16.0.0.0/4",
            "32.0.0.0/3",
            "64.0.0.0/2",
            "128.0.0.0/3",
            "160.0.0.0/5",
            "168.0.0.0/6",
            "172.0.0.0/12",
            "172.32.0.0/11",
            "172.64.0.0/10",
            "172.128.0.0/9",
            "173.0.0.0/8",
            "174.0.0.0/7",
            "176.0.0.0/4",
            "192.0.0.0/9",
            "192.128.0.0/11",
            "192.160.0.0/13",
            "192.169.0.0/16",
            "192.170.0.0/15",
            "192.172.0.0/14",
            "192.176.0.0/12",
            "192.192.0.0/10",
            "193.0.0.0/8",
            "194.0.0.0/7",
            "196.0.0.0/6",
            "200.0.0.0/5",
            "208.0.0.0/4",
        )

    val LAN_BYPASS_BASE = IPV4_PUBLIC + IPV6_PUBLIC
    val ALL_IPS = listOf(IPV4_ALL_NETWORKS, IPV6_ALL_NETWORKS)

    fun calculateLanBypass(dnsServers: Collection<String>): List<String> {
        val result = LAN_BYPASS_BASE.toMutableSet()

        dnsServers.forEach { dns ->
            val host = parseHost(dns) ?: return@forEach

            if (host.isLocal) {
                val prefix = if (host.isIPv4) 32 else 128
                result += "${host.toNormalizedString()}/$prefix"
            }
        }

        return result.sorted()
    }

    private fun parseHost(dns: String): IPAddress? {
        return try {
            val ipString = IPAddressString(dns.trim())
            if (!ipString.isValid) null else ipString.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
