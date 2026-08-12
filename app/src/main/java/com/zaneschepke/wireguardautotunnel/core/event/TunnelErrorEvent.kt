package com.zaneschepke.wireguardautotunnel.core.event

import com.wgtunnel.backend.exception.BackendException

sealed interface TunnelErrorEvent {
    data class VpnPermissionDenied(val tunnelId: Int) : TunnelErrorEvent

    data class InternalFailure(val tunnelId: Int, val message: String) : TunnelErrorEvent

    data class Socks5PortUnavailable(val tunnelId: Int, val port: Int) : TunnelErrorEvent

    data class HttpPortUnavailable(val tunnelId: Int, val port: Int) : TunnelErrorEvent

    data class ConfigMissingDns(val tunnelId: Int) : TunnelErrorEvent

    companion object {
        fun from(throwable: Throwable, id: Int): TunnelErrorEvent {
            return when (throwable) {
                is BackendException.Unauthorized -> {
                    VpnPermissionDenied(id)
                }
                is BackendException.InternalError -> {
                    InternalFailure(id, throwable.message)
                }
                is BackendException.Socks5PortUnavailable -> {
                    Socks5PortUnavailable(id, throwable.port)
                }
                is BackendException.HttpPortUnavailable -> {
                    HttpPortUnavailable(id, throwable.port)
                }
                is BackendException.ConfigMissingDNS -> {
                    ConfigMissingDns(id)
                }
                else -> InternalFailure(id, throwable.message ?: "Unknown")
            }
        }
    }
}
