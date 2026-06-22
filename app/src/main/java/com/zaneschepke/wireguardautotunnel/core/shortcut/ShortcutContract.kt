package com.zaneschepke.wireguardautotunnel.core.shortcut

object ShortcutContract {

    const val EXTRA_SHORTCUT_TYPE = "com.zaneschepke.wireguardautotunnel.shortcut.TYPE"

    const val EXTRA_TUNNEL_NAME = "tunnelName"

    const val EXTRA_CLASS_NAME = "className"

    enum class ShortcutType(val value: String) {
        TUNNEL("tunnel"),
        AUTO_TUNNEL("auto_tunnel"),
    }

    enum class Action {
        START,
        STOP,
    }

    object Legacy {

        const val TUNNEL_PROVIDER_NAME = "TunnelProvider"

        const val AUTO_TUNNEL_SERVICE_CLASS_NAME = "AutoTunnelService"

        const val TUNNEL_SERVICE_NAME = "WireGuardTunnelService"

        const val AUTO_TUNNEL_SERVICE_NAME = "WireGuardConnectivityWatcherService"
    }
}
