package com.zaneschepke.wireguardautotunnel.domain.policy

import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class StopOnUnreachablePolicyTest {

    @Test
    fun `grace includes backend recovery stages before seamless bounce`() {
        val general =
            GeneralSettings(seamlessRecoveryEnabled = true, seamlessRecoveryBounceDelaySec = 30)

        assertEquals(45_000L, StopOnUnreachablePolicy.gracePeriodMs(tunnel(), general))
        assertEquals(
            61_000L,
            StopOnUnreachablePolicy.gracePeriodMs(tunnel(dynamicDns = true), general),
        )
        assertEquals(
            61_000L,
            StopOnUnreachablePolicy.gracePeriodMs(tunnel(ipv4Fallback = true), general),
        )
        assertEquals(
            69_000L,
            StopOnUnreachablePolicy.gracePeriodMs(
                tunnel(dynamicDns = true, ipv4Fallback = true),
                general,
            ),
        )
    }

    @Test
    fun `grace still allows non-seamless recovery stages`() {
        val general = GeneralSettings(seamlessRecoveryEnabled = false)

        assertEquals(
            39_000L,
            StopOnUnreachablePolicy.gracePeriodMs(
                tunnel(dynamicDns = true, ipv4Fallback = true),
                general,
            ),
        )
    }

    @Test
    fun `maximum grace reflects the slowest configured tunnel`() {
        val general =
            GeneralSettings(seamlessRecoveryEnabled = true, seamlessRecoveryBounceDelaySec = 30)

        assertEquals(
            69_000L,
            StopOnUnreachablePolicy.maximumGracePeriodMs(
                listOf(tunnel(), tunnel(dynamicDns = true, ipv4Fallback = true)),
                general,
            ),
        )
    }

    private fun tunnel(
        dynamicDns: Boolean = false,
        ipv4Fallback: Boolean = false,
    ) =
        TunnelConfig(
            name = "test",
            isDDNSTunnel = dynamicDns,
            isIpv6Preferred = ipv4Fallback,
        )
}
