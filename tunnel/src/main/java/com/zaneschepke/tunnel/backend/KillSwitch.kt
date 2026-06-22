package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.model.KillSwitchConfig

internal interface KillSwitch {
    fun setKillSwitch(config: KillSwitchConfig?)

    fun startHevSocks5Bridge(port: Int, pass: String)

    fun stopHevSocks5Bridge()
}
