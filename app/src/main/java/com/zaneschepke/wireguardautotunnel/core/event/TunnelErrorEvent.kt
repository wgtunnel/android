package com.zaneschepke.wireguardautotunnel.core.event

import com.zaneschepke.tunnel.util.BackendException

sealed interface TunnelErrorEvent {
    data class VpnPermissionDenied(val tunnelId: Int) : TunnelErrorEvent

    data class StateConflict(val tunnelId: Int, val message: String) : TunnelErrorEvent

    data class InternalFailure(val tunnelId: Int?, val message: String) : TunnelErrorEvent

    companion object {
        fun from(throwable: Throwable, id: Int?): TunnelErrorEvent {
            return when (throwable) {
                is BackendException.StateConflict -> StateConflict(id ?: -1, throwable.message)
                is BackendException.Unauthorized -> InternalFailure(id, "Unauthorized")
                else -> InternalFailure(id, throwable.message ?: "Unknown")
            }
        }
    }
}
