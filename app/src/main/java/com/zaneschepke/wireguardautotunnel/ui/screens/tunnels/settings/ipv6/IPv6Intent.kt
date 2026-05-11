package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.ipv6

sealed class IPv6Intent {
    data class ToggleIpv6Preferred(val value: Boolean) : IPv6Intent()

    data class ToggleRestore(val value: Boolean) : IPv6Intent()

    data class ToggleFallback(val value: Boolean) : IPv6Intent()
}
