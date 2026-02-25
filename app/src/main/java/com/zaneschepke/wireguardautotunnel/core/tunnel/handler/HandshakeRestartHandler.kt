package com.zaneschepke.wireguardautotunnel.core.tunnel.handler

import com.zaneschepke.networkmonitor.ActiveNetwork
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelStatus
import com.zaneschepke.wireguardautotunnel.domain.events.BackendMessage
import com.zaneschepke.wireguardautotunnel.data.model.MaxAttemptsAction
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelRestartProgress
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelState
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class HandshakeRestartHandler(
    private val activeTunnels: StateFlow<Map<Int, TunnelState>>,
    private val tunnelsRepository: TunnelRepository,
    private val monitoringSettingsRepository: MonitoringSettingsRepository,
    private val localMessageEvents: MutableSharedFlow<Pair<String?, BackendMessage>>,
    private val restartTunnel: suspend (Int) -> Unit,
    private val stopTunnel: suspend (Int) -> Unit,
    private val networkMonitor: NetworkMonitor,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val jobs = ConcurrentHashMap<Int, Job>()

    // Tracks restart timestamps per tunnel across job restarts for rate limiting
    private val restartTimestamps = ConcurrentHashMap<Int, ArrayDeque<Long>>()

    // Tracks per-tunnel restart progress (active restart or post-restart cooldown)
    // and exposes it to the UI
    private val _restartProgress = MutableStateFlow<Map<Int, TunnelRestartProgress>>(emptyMap())
    val restartProgress: StateFlow<Map<Int, TunnelRestartProgress>> = _restartProgress.asStateFlow()

    // Counts total restarts per tunnel since it was activated (reset on manual stop)
    private val _restartCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val restartCounts: StateFlow<Map<Int, Int>> = _restartCounts.asStateFlow()

    // Tracks tunnels currently in a degraded state (stale handshake / ping failure detected)
    // Used to emit ConnectionRestored / ConnectionPermanentlyLost events
    private val degradedTunnels = ConcurrentHashMap<Int, BackendMessage.RestartReason>()

    // Emits Unit after NETWORK_RECOVERY_GRACE_MS on any network interface change
    // (Disconnected→WiFi/Cellular, WiFi→Cellular, Cellular→WiFi, etc.)
    // — wakes up the monitoring loop early instead of waiting ~3.5 min for stale detection.
    private val networkChangeFlow: Flow<Unit> =
        networkMonitor.connectivityStateFlow
            .map {
                when (it.activeNetwork) {
                    is ActiveNetwork.Disconnected -> null
                    is ActiveNetwork.Wifi -> "wifi"
                    is ActiveNetwork.Cellular -> "cellular"
                    is ActiveNetwork.Ethernet -> "ethernet"
                }
            }
            .distinctUntilChanged()
            .drop(1) // skip initial state at startup to avoid spurious trigger
            .filterNotNull() // ignore disconnect events — only react when a network is available
            .onEach {
                Timber.d("Network interface changed ($it) — waiting ${NETWORK_RECOVERY_GRACE_MS}ms grace period")
                delay(NETWORK_RECOVERY_GRACE_MS)
            }
            .map { }

    init {
        applicationScope.launch(ioDispatcher) {
            combine(activeTunnels, monitoringSettingsRepository.flow) { active, settings ->
                    active to settings
                }
                .collect { (activeTuns, settings) ->
                    mutex.withLock {
                        val activeIds =
                            if (settings.isRestartOnHandshakeTimeoutEnabled) {
                                activeTuns.keys.toSet()
                            } else {
                                emptySet()
                            }

                        (jobs.keys - activeIds).forEach { id ->
                            if (_restartProgress.value.containsKey(id)) {
                                Timber.d(
                                    "Skipping shutdown for tunnelId: $id (auto-restart in progress)"
                                )
                                return@forEach
                            }
                            Timber.d(
                                "Shutting down handshake restart monitoring job for tunnelId: $id"
                            )
                            jobs.remove(id)?.cancel()
                        }

                        activeIds.forEach { id ->
                            if (jobs.containsKey(id)) return@forEach
                            val tunStateFlow =
                                activeTunnels
                                    .map { it[id] }
                                    .stateIn(applicationScope + ioDispatcher)
                            Timber.d("Starting handshake restart monitoring job for tunnelId: $id")
                            jobs[id] =
                                applicationScope.launch(ioDispatcher) {
                                    monitorHandshake(id, tunStateFlow)
                                }
                        }
                    }
                }
        }
    }

    /**
     * Called when a tunnel is manually stopped from the UI.
     * Cancels any in-progress auto-restart and clears history so the rate limit resets.
     */
    fun cancelAndClear(tunnelId: Int) {
        Timber.d("Manual stop for tunnel $tunnelId — cancelling restart job and clearing history")
        jobs.remove(tunnelId)?.cancel()
        _restartProgress.update { it - tunnelId }
        _restartCounts.update { it - tunnelId }
        restartTimestamps.remove(tunnelId)
        if (degradedTunnels.remove(tunnelId) != null) {
            applicationScope.launch(ioDispatcher) {
                localMessageEvents.emit(null to BackendMessage.ConnectionCancelled)
            }
        }
    }

    /**
     * Returns true if the tunnel state should trigger a restart:
     * - Stale WireGuard handshake (no handshake in ~3.5 min)
     * - Ping failure (ALL pinged peers are unreachable)
     */
    private fun shouldTrigger(state: TunnelState, isPingMonitoringEnabled: Boolean = true): Boolean {
        if (state.status !is TunnelStatus.Up) return false
        if (state.statistics?.isTunnelStale() == true) return true
        if (isPingMonitoringEnabled) {
            state.pingStates?.let { pings ->
                val attemptedPings = pings.values.filter { it.lastPingAttemptMillis != null }
                if (attemptedPings.isNotEmpty() && attemptedPings.all { !it.isReachable }) return true
            }
        }
        return false
    }

    private fun triggerReason(
        state: TunnelState,
        isPingMonitoringEnabled: Boolean,
    ): BackendMessage.RestartReason {
        if (state.statistics?.isTunnelStale() == true) return BackendMessage.RestartReason.STALE_HANDSHAKE
        if (isPingMonitoringEnabled) {
            state.pingStates?.let { pings ->
                val attempted = pings.values.filter { it.lastPingAttemptMillis != null }
                if (attempted.isNotEmpty() && attempted.all { !it.isReachable })
                    return BackendMessage.RestartReason.PING_FAILURE
            }
        }
        return BackendMessage.RestartReason.STALE_HANDSHAKE
    }

    private suspend fun monitorHandshake(
        tunnelId: Int,
        tunStateFlow: StateFlow<TunnelState?>,
    ) {
        // Apply cooldown if we recently restarted this tunnel (e.g. job recreated after manual toggle)
        restartTimestamps[tunnelId]?.lastOrNull()?.let { lastRestart ->
            val settings = monitoringSettingsRepository.getMonitoringSettings()
            val attemptsDone = restartTimestamps[tunnelId]?.size ?: 1
            val cooldownSec = computeCooldown(settings.restartCooldownSeconds, attemptsDone, settings.isBackoffEnabled)
            val cooldownRemaining = (lastRestart + cooldownSec * 1_000L) - System.currentTimeMillis()
            if (cooldownRemaining > 0) {
                Timber.d(
                    "Cooldown active for tunnel $tunnelId, " +
                        "waiting ${cooldownRemaining}ms before monitoring"
                )
                delay(cooldownRemaining)
            }
        }

        // Counts consecutive ping-failure intervals for the current failure streak.
        // Resets to 0 when the tunnel becomes healthy or after a restart attempt.
        var pingFailureStreak = 0

        while (true) {
            val state = tunStateFlow.value ?: break
            val settings = monitoringSettingsRepository.getMonitoringSettings()

            if (!shouldTrigger(state, settings.isPingMonitoringEnabled)) {
                // Tunnel healthy — emit ConnectionRestored if it was previously degraded
                pingFailureStreak = 0
                if (degradedTunnels.remove(tunnelId) != null) {
                    val tunnelName = tunnelsRepository.getById(tunnelId)?.name
                    localMessageEvents.emit(tunnelName to BackendMessage.ConnectionRestored)
                }
                // Wait for either a trigger condition or a network reconnection event
                merge(
                        tunStateFlow
                            .filter { it == null || shouldTrigger(it, settings.isPingMonitoringEnabled) }
                            .take(1)
                            .map { },
                        networkChangeFlow.take(1),
                    )
                    .first()
                continue
            }

            val reason = triggerReason(state, settings.isPingMonitoringEnabled)

            // For ping failures: require N consecutive failing intervals before restarting.
            // Stale-handshake restarts are not subject to this threshold (WG already waits ~3.5 min).
            if (reason == BackendMessage.RestartReason.PING_FAILURE) {
                pingFailureStreak++
                if (pingFailureStreak < settings.pingFailuresBeforeRestart) {
                    Timber.d(
                        "Ping failure streak $pingFailureStreak/${settings.pingFailuresBeforeRestart} " +
                            "for tunnel $tunnelId — waiting for next ping cycle"
                    )
                    // Wait for a NEW ping attempt (not just any state update like statistics).
                    // tunStateFlow emits on every stats poll (bytes, handshake time…), so using
                    // drop(1).first() would return almost immediately. We need to wait until
                    // lastPingAttemptMillis actually advances to a new cycle.
                    val currentPingTime = state.pingStates
                        ?.values
                        ?.mapNotNull { it.lastPingAttemptMillis }
                        ?.maxOrNull()
                    tunStateFlow.first { newState ->
                        // Also break out if the tunnel is no longer triggering (recovered,
                        // or ping was disabled so pingStates are cleared).
                        if (newState == null || !shouldTrigger(newState, settings.isPingMonitoringEnabled)) return@first true
                        val newPingTime = newState.pingStates
                            ?.values
                            ?.mapNotNull { it.lastPingAttemptMillis }
                            ?.maxOrNull()
                        newPingTime != null && newPingTime != currentPingTime
                    }
                    continue
                }
                pingFailureStreak = 0
            }

            val now = System.currentTimeMillis()
            val timestamps = restartTimestamps.getOrPut(tunnelId) { ArrayDeque() }

            // Prune timestamps older than 1 hour
            while (timestamps.isNotEmpty() && timestamps.first() < now - ONE_HOUR_MS) {
                timestamps.removeFirst()
            }

            if (timestamps.size >= settings.maxHandshakeRestartAttempts) {
                Timber.w(
                    "Max restart attempts (${settings.maxHandshakeRestartAttempts}) reached " +
                        "for tunnel $tunnelId within the last hour — waiting for recovery"
                )
                val permanentReason = degradedTunnels[tunnelId] ?: reason
                val tunnelName = tunnelsRepository.getById(tunnelId)?.name
                val isTunnelStopped = settings.maxAttemptsAction == MaxAttemptsAction.STOP_TUNNEL
                localMessageEvents.emit(
                    tunnelName to BackendMessage.ConnectionPermanentlyLost(
                        permanentReason,
                        settings.maxHandshakeRestartAttempts,
                        isTunnelStopped = isTunnelStopped,
                    )
                )
                when (settings.maxAttemptsAction) {
                    MaxAttemptsAction.DO_NOTHING -> {
                        // Wait until healthy again or tunnel goes down
                        tunStateFlow.first {
                            it == null || (it.status is TunnelStatus.Up && !shouldTrigger(it, settings.isPingMonitoringEnabled))
                        }
                        restartTimestamps.remove(tunnelId)
                        if (degradedTunnels.remove(tunnelId) != null) {
                            val tunnelNameRestored = tunnelsRepository.getById(tunnelId)?.name
                            localMessageEvents.emit(tunnelNameRestored to BackendMessage.ConnectionRestored)
                        }
                        continue
                    }
                    MaxAttemptsAction.STOP_TUNNEL -> {
                        // Clear degradedTunnels before stopping so cancelAndClear won't emit
                        // ConnectionCancelled — the "max restarts reached" notification stays visible
                        degradedTunnels.remove(tunnelId)
                        runCatching { stopTunnel(tunnelId) }.onFailure { e ->
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to stop tunnel $tunnelId after max restart attempts")
                        }
                        return
                    }
                }
            }
            val attempt = timestamps.size + 1
            Timber.i(
                "Auto-restarting tunnel $tunnelId due to $reason " +
                    "(attempt $attempt/${settings.maxHandshakeRestartAttempts})"
            )

            val failingTargets =
                if (reason == BackendMessage.RestartReason.PING_FAILURE) {
                    state.pingStates
                        ?.filter { (_, ping) -> ping.lastPingAttemptMillis != null && !ping.isReachable }
                        ?.values
                        ?.map { it.pingTarget }
                        ?: emptyList()
                } else emptyList()

            _restartProgress.update {
                it +
                    (tunnelId to
                        TunnelRestartProgress(
                            isRestarting = true,
                            attemptNumber = attempt,
                            maxAttempts = settings.maxHandshakeRestartAttempts,
                            reason = reason,
                            failingPingTargets = failingTargets,
                        ))
            }
            // Record the attempt before restarting so the rate limit applies even if the restart fails
            timestamps.addLast(now)
            _restartCounts.update { it + (tunnelId to (it.getOrDefault(tunnelId, 0) + 1)) }
            val tunnelName = tunnelsRepository.getById(tunnelId)?.name
            degradedTunnels[tunnelId] = reason
            localMessageEvents.emit(
                tunnelName to BackendMessage.ConnectionDegrading(reason, attempt, settings.maxHandshakeRestartAttempts)
            )
            runCatching {
                    restartTunnel(tunnelId)
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Timber.e(e, "Failed to restart tunnel $tunnelId after $reason")
                }
            // Post-restart cooldown: wait remaining time before next check
            val cooldownSec = computeCooldown(settings.restartCooldownSeconds, attempt, settings.isBackoffEnabled)
            val cooldownEnd = now + cooldownSec * 1_000L
            val cooldownRemaining = cooldownEnd - System.currentTimeMillis()
            _restartProgress.update {
                it +
                    (tunnelId to
                        TunnelRestartProgress(
                            isRestarting = false,
                            attemptNumber = attempt,
                            maxAttempts = settings.maxHandshakeRestartAttempts,
                            nextRetryAtMillis = if (cooldownRemaining > 0 && attempt < settings.maxHandshakeRestartAttempts) cooldownEnd else 0L,
                            reason = reason,
                            failingPingTargets = failingTargets,
                        ))
            }
            if (cooldownRemaining > 0) {
                Timber.d("Post-restart cooldown for tunnel $tunnelId: waiting ${cooldownRemaining}ms")
                // Cancel early if the tunnel becomes healthy before the cooldown expires
                // (pingStates != null ensures at least one ping cycle has completed post-restart)
                withTimeoutOrNull(cooldownRemaining) {
                    tunStateFlow.filterNotNull().first { s ->
                        s.pingStates != null && !shouldTrigger(s, settings.isPingMonitoringEnabled)
                    }
                }
            }
            _restartProgress.update { it - tunnelId }
        }
    }

    companion object {
        const val ONE_HOUR_MS = 3_600_000L
        const val NETWORK_RECOVERY_GRACE_MS = 10_000L
        const val MAX_BACKOFF_SECONDS = 300L

        /**
         * Returns the cooldown in seconds for the given attempt number.
         * With backoff: base × 2^(attempt-1), capped at MAX_BACKOFF_SECONDS.
         * Without backoff: always returns base.
         */
        fun computeCooldown(baseSec: Int, attempt: Int, backoffEnabled: Boolean): Long {
            if (!backoffEnabled) return baseSec.toLong()
            val shift = (attempt - 1).coerceAtMost(30) // guard against overflow
            return minOf(baseSec.toLong() shl shift, MAX_BACKOFF_SECONDS)
        }
    }
}
