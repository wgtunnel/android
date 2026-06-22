package com.zaneschepke.wireguardautotunnel.service.autotunnel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AutoTunnelStateHolder {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    fun setActive(active: Boolean) {
        _active.value = active
    }
}
