package com.zaneschepke.wireguardautotunnel.domain.events

import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.util.StringValue

sealed class BackendMessage {

    enum class RestartReason { STALE_HANDSHAKE, PING_FAILURE }

    data object DynamicDnsSuccess : BackendMessage()

    data class HandshakeRestarted(val reason: RestartReason) : BackendMessage()

    fun toStringRes() =
        when (this) {
            DynamicDnsSuccess -> R.string.ddns_success_message
            is HandshakeRestarted ->
                when (reason) {
                    RestartReason.STALE_HANDSHAKE -> R.string.handshake_restart_stale
                    RestartReason.PING_FAILURE -> R.string.handshake_restart_ping
                }
        }

    fun toStringValue() = StringValue.StringResource(this.toStringRes())
}
