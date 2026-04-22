package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.model.KillSwitchConfig

interface KillSwitch {
    fun setKillSwitch(config: KillSwitchConfig?)
    fun startHevSocks5Bridge()
    fun stopHevSocks5Bridge()
}