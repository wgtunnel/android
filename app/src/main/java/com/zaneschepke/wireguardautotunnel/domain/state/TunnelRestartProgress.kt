package com.zaneschepke.wireguardautotunnel.domain.state

import com.zaneschepke.wireguardautotunnel.domain.events.BackendMessage

data class TunnelRestartProgress(
    val isRestarting: Boolean = false,
    val attemptNumber: Int = 0,
    val maxAttempts: Int = 0,
    // Epoch ms when the post-restart cooldown ends; 0 = no pending cooldown
    val nextRetryAtMillis: Long = 0L,
    val reason: BackendMessage.RestartReason? = null,
    // Non-empty when reason == PING_FAILURE, lists the unreachable targets
    val failingPingTargets: List<String> = emptyList(),
)
