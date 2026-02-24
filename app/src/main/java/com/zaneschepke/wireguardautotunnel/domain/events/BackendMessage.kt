package com.zaneschepke.wireguardautotunnel.domain.events

import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.util.StringValue

sealed class BackendMessage {

    enum class RestartReason { STALE_HANDSHAKE, PING_FAILURE }

    data object DynamicDnsSuccess : BackendMessage()

    data class ConnectionDegrading(val reason: RestartReason, val attempt: Int, val maxAttempts: Int) : BackendMessage()

    data object ConnectionRestored : BackendMessage()

    data class ConnectionPermanentlyLost(val reason: RestartReason, val totalAttempts: Int) : BackendMessage()

    data object ConnectionCancelled : BackendMessage()

    fun toStringValue(): StringValue? =
        when (this) {
            DynamicDnsSuccess -> StringValue.StringResource(R.string.ddns_success_message)
            else -> null
        }
}
