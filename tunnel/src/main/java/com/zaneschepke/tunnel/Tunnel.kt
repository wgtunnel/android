package com.zaneschepke.tunnel

interface Tunnel {
    val id: Int
    val name: String
    val isMetered: Boolean

    val ipStrategy: IpStrategy
    val features: Set<Feature>

    fun updateState(state: State)

    sealed interface State {
        sealed class Up : State {
            data object Healthy : Up()

            data object ResolvingDns : Up()

            data object HandshakeFailure : Up()
        }

        data object Down : State

        data object Starting : State

        data object Stopping : State
    }

    sealed interface IpStrategy {
        data object Ipv4Only : IpStrategy

        data class PreferIpv6(
            val fallbackToIpv4Enabled: Boolean = true,
            val recoveryEnabled: Boolean = true,
        ) : IpStrategy
    }

    sealed interface Feature {
        data object DynamicDNS : Feature

        data class ActiveConfigMonitor(val intervalSeconds: Int = 1) : Feature
    }
}
