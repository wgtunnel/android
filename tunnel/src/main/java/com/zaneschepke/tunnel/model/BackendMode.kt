package com.zaneschepke.tunnel.model

import com.zaneschepke.wireguardautotunnel.parser.Config

sealed class BackendMode {
    abstract val config: Config

    abstract fun withConfig(config: Config): BackendMode

    sealed class Proxy : BackendMode() {

        data class Standard(override val config: Config, val proxyConfig: ProxyConfig) : Proxy() {
            override fun withConfig(config: Config) = copy(config = config)
        }

        data class KillSwitchPrimary(
            override val config: Config,
            val killSwitchConfig: KillSwitchConfig,
        ) : Proxy() {
            override fun withConfig(config: Config) = copy(config = config)
        }
    }

    data class Vpn(override val config: Config) : BackendMode() {
        override fun withConfig(config: Config) = copy(config = config)
    }
}
