package com.zaneschepke.wireguardautotunnel.service.autotunnel

import com.zaneschepke.wireguardautotunnel.domain.policy.StopOnUnreachablePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class UnreachableTunnelSnapshot(
    val enabled: Boolean = false,
    val networkKey: String? = null,
    val armFailureGracePeriodsMs: Map<Int, Long> = emptyMap(),
    val keepFailureIds: Set<Int> = emptySet(),
    val connectedIds: Set<Int> = emptySet(),
)

/**
 * Stops tunnels whose handshake keeps failing for the snapshot's grace period, then keeps them in
 * [suspended] for [cooldownMs] so Auto-Tunnel does not start them again right away.
 */
internal class UnreachableTunnelMonitor(
    private val scope: CoroutineScope,
    private val stopTunnel: suspend (Int) -> Result<Unit>,
    private val onStopped: suspend (Int) -> Unit,
    private val cooldownMs: Long = StopOnUnreachablePolicy.COOLDOWN_MS,
    private val stopRetryDelayMs: Long = StopOnUnreachablePolicy.STOP_RETRY_DELAY_MS,
) {
    private val mutex = Mutex()
    private val episodes = mutableMapOf<Int, Job>()
    private val notifiedIds = mutableSetOf<Int>()
    private var snapshot = UnreachableTunnelSnapshot()

    private val _suspended = MutableStateFlow<Set<Int>>(emptySet())
    val suspended: StateFlow<Set<Int>> = _suspended.asStateFlow()

    suspend fun update(next: UnreachableTunnelSnapshot) = mutex.withLock {
        val networkChanged = snapshot.networkKey != next.networkKey
        snapshot = next

        if (!next.enabled || networkChanged) {
            episodes.keys.toList().forEach(::clearLocked)
            notifiedIds.clear()
        }
        if (!next.enabled) return@withLock

        next.connectedIds.forEach { id ->
            clearLocked(id)
            notifiedIds -= id
        }

        // Once suspended, an episode is stopping or cooling down and outlives the tunnel.
        episodes.keys
            .filter { it !in next.keepFailureIds && it !in _suspended.value }
            .forEach(::clearLocked)

        next.armFailureGracePeriodsMs
            .filterKeys { episodes[it]?.isActive != true }
            .forEach { (id, gracePeriodMs) ->
                episodes[id] = scope.launch { runEpisode(id, gracePeriodMs) }
            }
    }

    private suspend fun runEpisode(id: Int, gracePeriodMs: Long) {
        delay(gracePeriodMs)

        try {
            while (true) {
                val shouldStop = mutex.withLock {
                    (id in snapshot.keepFailureIds).also { if (it) setSuspended(id, true) }
                }
                if (!shouldStop) return

                // A half-finished backend stop is worse than an unwanted one.
                val result = withContext(NonCancellable) { stopTunnel(id) }
                currentCoroutineContext().ensureActive()
                if (result.isSuccess) {
                    notifyStoppedOnce(id)
                    delay(cooldownMs)
                    return
                }

                setSuspended(id, false)
                delay(stopRetryDelayMs)
            }
        } finally {
            setSuspended(id, false)
        }
    }

    private suspend fun notifyStoppedOnce(id: Int) {
        if (mutex.withLock { id in notifiedIds }) return
        try {
            onStopped(id)
            mutex.withLock { notifiedIds.add(id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to notify for stopped unreachable tunnel $id")
        }
    }

    private fun clearLocked(id: Int) {
        episodes.remove(id)?.cancel()
        setSuspended(id, false)
    }

    private fun setSuspended(id: Int, suspended: Boolean) {
        _suspended.update { if (suspended) it + id else it - id }
    }
}
