package com.zaneschepke.tunnel.state

sealed class BootstrapState {
    data object None : BootstrapState()

    data object ResolvingDns : BootstrapState()

    data object Complete : BootstrapState()

    data class Failed(val error: Throwable? = null) : BootstrapState()
}
