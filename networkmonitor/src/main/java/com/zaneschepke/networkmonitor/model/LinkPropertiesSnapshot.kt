package com.zaneschepke.networkmonitor.model

import android.net.LinkProperties

data class LinkPropertiesSnapshot(
    val ipv4Addresses: List<String> = emptyList(),
    val hasDefaultIpv4Route: Boolean = false,
    val dnsServerCount: Int = 0,
) {
    companion object {
        fun from(lp: LinkProperties?): LinkPropertiesSnapshot {
            if (lp == null) return LinkPropertiesSnapshot()

            val ipv4Addrs =
                lp.linkAddresses
                    .mapNotNull { la ->
                        val addr = la.address
                        if (addr is java.net.Inet4Address) addr.hostAddress else null
                    }
                    .sorted()

            val hasDefaultV4 =
                lp.routes.any { route ->
                    route.isDefaultRoute && route.gateway is java.net.Inet4Address
                }

            return LinkPropertiesSnapshot(
                ipv4Addresses = ipv4Addrs,
                hasDefaultIpv4Route = hasDefaultV4,
                dnsServerCount = lp.dnsServers.size,
            )
        }
    }
}
