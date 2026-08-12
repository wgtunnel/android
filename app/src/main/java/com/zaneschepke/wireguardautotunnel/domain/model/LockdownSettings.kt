package com.zaneschepke.wireguardautotunnel.domain.model

import AllowedIpsCalculator
import com.wgtunnel.backend.model.KillSwitchConfig

data class LockdownSettings(
    val id: Long = 0L,
    val bypassLan: Boolean = false,
    val metered: Boolean = false,
    val dualStack: Boolean = false,
) {
    fun toKillSwitchConfig(): KillSwitchConfig {
        return KillSwitchConfig(
            allowedIps = if (bypassLan) AllowedIpsCalculator.LAN_BYPASS_BASE else emptySet(),
            metered = metered,
            dualStack = dualStack,
        )
    }
}
