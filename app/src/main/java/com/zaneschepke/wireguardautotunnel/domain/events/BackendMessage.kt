package com.zaneschepke.wireguardautotunnel.domain.events

import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.util.StringValue

sealed class BackendMessage {

    enum class RestartReason {
        PING_FAILURE
    }

    data object DynamicDnsSuccess : BackendMessage()

    data class ConnectionDegrading(
        val reason: RestartReason,
        val attempt: Int,
        val maxAttempts: Int,
    ) : BackendMessage()

    data object ConnectionRestored : BackendMessage()

    data class ConnectionPermanentlyLost(
        val reason: RestartReason,
        val totalAttempts: Int,
        val isTunnelStopped: Boolean = false,
    ) : BackendMessage()

    data object ConnectionCancelled : BackendMessage()

    data class SwitchedToFallback(val fromTunnelName: String, val toTunnelName: String) :
        BackendMessage()

    fun toStringValue(): StringValue? =
        when (this) {
            DynamicDnsSuccess -> StringValue.StringResource(R.string.ddns_success_message)
            is ConnectionDegrading ->
                StringValue.StringResource(
                    R.string.snackbar_connection_degrading,
                    attempt.toString(),
                    maxAttempts.toString(),
                )
            ConnectionRestored -> StringValue.StringResource(R.string.snackbar_connection_restored)
            is ConnectionPermanentlyLost ->
                if (isTunnelStopped) {
                    StringValue.StringResource(
                        R.string.snackbar_connection_lost_stopped,
                        totalAttempts.toString(),
                    )
                } else {
                    StringValue.StringResource(
                        R.string.snackbar_connection_lost,
                        totalAttempts.toString(),
                    )
                }
            is SwitchedToFallback ->
                StringValue.StringResource(R.string.snackbar_switched_to_fallback, toTunnelName)
            ConnectionCancelled -> null
        }
}
