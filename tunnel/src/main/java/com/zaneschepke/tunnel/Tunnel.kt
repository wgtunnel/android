package com.zaneschepke.tunnel

interface Tunnel {
    val id: Int
    val name: String
    val isMetered: Boolean
    val scriptsEnabled: Boolean

    val ipStrategy: IpStrategy
    val features: Set<Feature>

    /**
     * Domains whose DNS queries should be resolved through the tunnel's DNS server. When non-empty,
     * all other DNS queries are resolved via the underlying system resolver outside the tunnel. When
     * empty, DNS routing falls back to the default behavior (all DNS to the tunnel DNS server).
     */
    val splitDnsDomains: Set<String>

    fun updateState(state: State)

    sealed interface State {
        sealed class Up : State {
            data object Healthy : Up()

            data object HandshakeFailure : Up()
        }

        data object Down : State

        data object Starting : State

        data object Stopping : State

        companion object {
            fun fromNative(code: Int): State? {
                return when (code) {
                    0 -> Up.Healthy
                    1 -> Up.HandshakeFailure
                    99 -> Down
                    else -> null
                }
            }
        }
    }

    sealed interface IpStrategy {
        data object Ipv4Only : IpStrategy

        data class PreferIpv6(val recoveryEnabled: Boolean = true) : IpStrategy
    }

    sealed interface Feature {

        data class ActiveConfigMonitor(val intervalSeconds: Int = 3) : Feature

        data object SeamlessRecovery : Feature
    }
}
