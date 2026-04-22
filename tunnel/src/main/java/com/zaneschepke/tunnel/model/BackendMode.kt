package com.zaneschepke.tunnel.model

import com.zaneschepke.wireguardautotunnel.parser.Config

sealed class BackendMode {
    abstract val config: Config

    sealed class Proxy : BackendMode() {

        data class Standard(
            override val config: Config,
            val proxyConfig: ProxyConfig
        ) : Proxy()

        data class KillSwitchPrimary(
            override val config: Config
        ) : Proxy()
    }

    data class Vpn(
        override val config: Config
    ) : BackendMode()

    data class Kernel(
        override val config: Config
    ) : BackendMode()
}