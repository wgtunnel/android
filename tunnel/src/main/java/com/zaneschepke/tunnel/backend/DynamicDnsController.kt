package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.model.DnsBootstrapResult
import com.zaneschepke.tunnel.model.PublicKey

class DynamicDnsController(
    private val stabilityWindowMs: Long,
    private val failureWindowMs: Long,
    private val minResolveIntervalMs: Long,
) {

    private var lastStableHealthySinceMs = -1L
    private var failureWindowStartMs = -1L
    private var lastDnsResolveMs = 0L
    private var lastCache = emptyMap<PublicKey, DnsBootstrapResult>()

    fun shouldResolve(now: Long, isHealthy: Boolean, isHandshakeFailure: Boolean): Boolean {

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

        val rateLimited = now - lastDnsResolveMs >= minResolveIntervalMs

        return stableEnough && failureEnough && rateLimited
    }

    fun markResolved(now: Long) {
        lastDnsResolveMs = now
    }

    fun diff(resolved: Map<PublicKey, DnsBootstrapResult>): List<PublicKey> {

        val changed = buildList {
            for ((key, cache) in resolved) {
                val old = lastCache[key]
                if (old == null || old.ipv4 != cache.ipv4 || old.ipv6 != cache.ipv6) {
                    add(key)
                }
            }
        }

        if (changed.isNotEmpty()) {
            lastCache = resolved
        }

        return changed
    }
}
