package com.zaneschepke.wireguardautotunnel.domain.model

import com.zaneschepke.tunnel.model.KillSwitchConfig

data class LockdownSettings(
    val id: Long = 0L,
    val bypassLan: Boolean = false,
    val metered: Boolean = false,
    val dualStack: Boolean = false,
) {
    fun toKillSwitchConfig(): KillSwitchConfig {
        return KillSwitchConfig(
            allowedIps = if (bypassLan) TunnelConfig.LAN_BYPASS_ALLOWED_IPS else emptySet(),
            metered = metered,
            dualStack = dualStack,
        )
    }
}
