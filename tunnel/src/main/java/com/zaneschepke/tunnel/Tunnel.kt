package com.zaneschepke.tunnel

interface
Tunnel {
    val id: Int
    val name: String
    val isMetered: Boolean

    val preferIpv6: Boolean
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

    sealed interface Feature {
        data object DynamicDNS : Feature

        data class ActiveConfigMonitor(
            val intervalSeconds: Int = 1
        ) : Feature

        data class PingMonitor(
            val intervalSeconds: Int = 30,
            val attempts: Int = 3,
            val timeoutSeconds: Int? = null,
            val target: String? = null,
        ) : Feature
    }
}