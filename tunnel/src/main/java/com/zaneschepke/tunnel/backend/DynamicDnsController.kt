package com.zaneschepke.tunnel.backend

class DynamicDnsController(
    private val stabilityWindowMs: Long,
    private val failureWindowMs: Long,
    private val minCheckIntervalMs: Long,
) {
    private var lastStableHealthySinceMs = -1L
    private var failureWindowStartMs = -1L
    private var lastCheckMs = 0L

    fun shouldCheck(now: Long, isHealthy: Boolean, isHandshakeFailure: Boolean): Boolean {
        if (isHealthy) {
            lastStableHealthySinceMs = now
        }
        if (isHandshakeFailure) {
            if (failureWindowStartMs < 0) failureWindowStartMs = now
        } else {
            failureWindowStartMs = -1L
        }

        val stableEnough =
            lastStableHealthySinceMs > 0 && now - lastStableHealthySinceMs >= stabilityWindowMs
        val failureEnough =
            failureWindowStartMs > 0 && now - failureWindowStartMs >= failureWindowMs
        val rateLimited = now - lastCheckMs >= minCheckIntervalMs

        // Trigger on either long stable healthy period OR prolonged handshake failure
        return (stableEnough || failureEnough) && rateLimited
    }

    fun markChecked(now: Long) {
        lastCheckMs = now
    }
}
