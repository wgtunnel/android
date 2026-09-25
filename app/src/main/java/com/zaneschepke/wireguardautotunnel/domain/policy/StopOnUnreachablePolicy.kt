package com.zaneschepke.wireguardautotunnel.domain.policy

import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

object StopOnUnreachablePolicy {
    const val POST_RECOVERY_GRACE_PERIOD_MS = 15_000L
    const val COOLDOWN_MS = 300_000L
    const val STOP_RETRY_DELAY_MS = 15_000L

    // Mirrors the backend recovery stabilization window. DDNS and IPv4 fallback each add a
    // stabilization period after the shared initial period.
    private const val RECOVERY_STABILIZATION_PERIOD_MS = 8_000L

    fun gracePeriodMs(tunnel: TunnelConfig?, general: GeneralSettings): Long {
        val dynamicDnsRecovery = tunnel?.isDDNSTunnel == true
        val ipv4Fallback = tunnel?.isIpv6Preferred == true
        val recoveryPreparationMs =
            if (dynamicDnsRecovery || ipv4Fallback) {
                RECOVERY_STABILIZATION_PERIOD_MS +
                    (if (dynamicDnsRecovery) RECOVERY_STABILIZATION_PERIOD_MS else 0L) +
                    (if (ipv4Fallback) RECOVERY_STABILIZATION_PERIOD_MS else 0L)
            } else {
                0L
            }
        val seamlessRecoveryMs =
            if (general.seamlessRecoveryEnabled) {
                general.seamlessRecoveryBounceDelaySec * 1_000L
            } else {
                0L
            }
        return recoveryPreparationMs + seamlessRecoveryMs + POST_RECOVERY_GRACE_PERIOD_MS
    }

    fun maximumGracePeriodMs(
        tunnels: List<TunnelConfig>,
        general: GeneralSettings,
    ): Long = tunnels.maxOfOrNull { gracePeriodMs(it, general) } ?: gracePeriodMs(null, general)
}
