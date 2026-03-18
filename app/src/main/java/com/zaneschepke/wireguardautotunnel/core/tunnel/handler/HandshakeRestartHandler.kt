package com.zaneschepke.wireguardautotunnel.core.tunnel.handler

import com.zaneschepke.wireguardautotunnel.core.tunnel.handler.TunnelMonitorHandler.Companion.CLOUDFLARE_IPV4_IP
import com.zaneschepke.wireguardautotunnel.data.model.MaxAttemptsAction
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelStatus
import com.zaneschepke.wireguardautotunnel.domain.events.BackendMessage
import com.zaneschepke.wireguardautotunnel.domain.model.MonitoringSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.state.FailureReason
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelRestartProgress
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelState
import com.zaneschepke.wireguardautotunnel.util.extensions.toMillis
import com.zaneschepke.wireguardautotunnel.util.network.NetworkUtils
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class HandshakeRestartHandler(
    private val activeTunnels: StateFlow<Map<Int, TunnelState>>,
    private val tunnelsRepository: TunnelRepository,
    private val monitoringSettingsRepository: MonitoringSettingsRepository,
    private val networkUtils: NetworkUtils,
    private val stopTunnel: suspend (Int) -> Unit,
    private val startTunnel: suspend (TunnelConfig) -> Unit,
    private val updateProgress: (Int, TunnelRestartProgress?) -> Unit,
    private val emitMessage: suspend (String?, BackendMessage) -> Unit,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val jobs = ConcurrentHashMap<Int, Job>()

    // Tracks tunnels that we stopped ourselves (so the outer collector doesn't cancel their job)
    private val restarting = ConcurrentHashMap<Int, Boolean>()

    init {
        applicationScope.launch(ioDispatcher) {
            activeTunnels.collect { activeTuns ->
                mutex.withLock {
                    val activeIds = activeTuns.keys.toSet()

                    val tunnelConfigs = tunnelsRepository.flow.first()
                    val knownIds = tunnelConfigs.map { it.id }.toSet()

                    // Cancel jobs for tunnels that left activeTunnels
                    // Skip tunnels we're actively restarting (they leave temporarily during
                    // stop/start)
                    // BUT always cancel if the tunnel was deleted from DB
                    (jobs.keys - activeIds).forEach { id ->
                        if (restarting[id] != true || id !in knownIds) {
                            Timber.d("HandshakeRestartHandler: tunnel $id gone, cancelling job")
                            jobs.remove(id)?.cancel()
                            restarting.remove(id)
                            updateProgress(id, null)
                        }
                    }

                    // Start a job for each newly active tunnel not already tracked
                    activeIds.forEach { id ->
                        if (jobs.containsKey(id)) return@forEach
                        val config = tunnelConfigs.find { it.id == id } ?: return@forEach
                        jobs[id] =
                            applicationScope.launch(ioDispatcher) { runRestartLoop(id, config) }
                    }
                }
            }
        }
    }

    /** Top-level loop for a single tunnel. Restarts whenever monitoring settings change. */
    private suspend fun runRestartLoop(tunnelId: Int, config: TunnelConfig) {
        monitoringSettingsRepository.flow.collectLatest { settings ->
            if (!settings.isAutoRestartActive()) {
                updateProgress(tunnelId, null)
                return@collectLatest
            }
            Timber.d("HandshakeRestartHandler: starting monitor for tunnel $tunnelId")
            try {
                monitorTunnel(tunnelId, config, settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "HandshakeRestartHandler: error monitoring tunnel $tunnelId")
            }
        }
    }

    /**
     * Monitoring state machine for one tunnel with fixed settings snapshot. Exits when max attempts
     * are reached or the coroutine is cancelled.
     */
    private suspend fun monitorTunnel(
        tunnelId: Int,
        config: TunnelConfig,
        settings: MonitoringSettings,
    ) {
        // Wait for tunnel to be UP before doing anything
        activeTunnels.mapNotNull { it[tunnelId] }.first { it.status is TunnelStatus.Up }

        val maxAttempts = settings.maxRestartAttempts
        var attempt = 0
        var totalRestarts = 0

        // Wait for the initial ping failure that triggers the first restart
        var pingTarget = awaitPingFailures(tunnelId, settings)

        while (true) {
            attempt++

            if (attempt > maxAttempts) {
                Timber.d(
                    "HandshakeRestartHandler: tunnel $tunnelId gave up after $maxAttempts attempts"
                )
                updateProgress(
                    tunnelId,
                    TunnelRestartProgress(
                        attemptNumber = maxAttempts,
                        maxAttempts = maxAttempts,
                        nextRetryAtMillis = 0L,
                        reason = BackendMessage.RestartReason.PING_FAILURE,
                        totalRestarts = totalRestarts,
                    ),
                )
                val isStopped = settings.maxAttemptsAction == MaxAttemptsAction.STOP_TUNNEL
                emitMessage(
                    config.name,
                    BackendMessage.ConnectionPermanentlyLost(
                        BackendMessage.RestartReason.PING_FAILURE,
                        maxAttempts,
                        isStopped,
                    ),
                )
                if (isStopped) {
                    mutex.withLock { restarting[tunnelId] = false }
                    runCatching { stopTunnel(tunnelId) }
                    updateProgress(tunnelId, null)
                    return
                }
                // DO_NOTHING: keep progress ("awaiting recovery"), wait for natural ping recovery
                activeTunnels
                    .mapNotNull { it[tunnelId]?.pingStates }
                    .distinctUntilChanged()
                    .first { pingStates ->
                        pingStates.values.isNotEmpty() && pingStates.values.all { it.isReachable }
                    }
                updateProgress(tunnelId, TunnelRestartProgress(totalRestarts = totalRestarts))
                emitMessage(config.name, BackendMessage.ConnectionRestored)
                attempt = 0
                pingTarget = awaitPingFailures(tunnelId, settings)
                continue
            }

            // RESTARTING
            Timber.d(
                "HandshakeRestartHandler: restarting tunnel $tunnelId (attempt $attempt/$maxAttempts)"
            )
            updateProgress(
                tunnelId,
                TunnelRestartProgress(
                    isRestarting = true,
                    attemptNumber = attempt,
                    maxAttempts = maxAttempts,
                    reason = BackendMessage.RestartReason.PING_FAILURE,
                    failingPingTargets = listOf(pingTarget),
                    totalRestarts = totalRestarts,
                ),
            )
            mutex.withLock { restarting[tunnelId] = true }
            runCatching { stopTunnel(tunnelId) }
                .onFailure {
                    Timber.e(it, "HandshakeRestartHandler: stop failed for tunnel $tunnelId")
                }
            delay(RESTART_SETTLE_DELAY_MS)

            // Abort if another tunnel took over while we were stopped (e.g. auto-tunnel switched)
            val currentActive = activeTunnels.value
            if (currentActive.isNotEmpty() && !currentActive.containsKey(tunnelId)) {
                Timber.d(
                    "HandshakeRestartHandler: tunnel $tunnelId superseded by another tunnel, aborting restart"
                )
                mutex.withLock { restarting[tunnelId] = false }
                updateProgress(tunnelId, null)
                return
            }

            // Fetch fresh config in case tunnel was modified since handler started
            val freshConfig = tunnelsRepository.getById(tunnelId) ?: config
            runCatching { startTunnel(freshConfig) }
                .onFailure {
                    Timber.e(it, "HandshakeRestartHandler: start failed for tunnel $tunnelId")
                }

            // Wait for tunnel to come back UP (30s safety timeout)
            val cameUp =
                withTimeoutOrNull(TUNNEL_UP_TIMEOUT_MS) {
                    activeTunnels.mapNotNull { it[tunnelId] }.first { it.status is TunnelStatus.Up }
                }
            mutex.withLock { restarting[tunnelId] = false }
            totalRestarts++

            if (cameUp == null) {
                Timber.w("HandshakeRestartHandler: tunnel $tunnelId did not come UP within timeout")
                // Count as failed verification, will retry on next loop iteration
                continue
            }

            // VERIFYING — short settle then direct ping, before any cooldown
            delay(VERIFY_SETTLE_DELAY_MS)
            Timber.d("HandshakeRestartHandler: verifying tunnel $tunnelId via ping to $pingTarget")
            updateProgress(
                tunnelId,
                TunnelRestartProgress(
                    isVerifying = true,
                    attemptNumber = attempt,
                    maxAttempts = maxAttempts,
                    reason = BackendMessage.RestartReason.PING_FAILURE,
                    failingPingTargets = listOf(pingTarget),
                    totalRestarts = totalRestarts,
                ),
            )

            val timeout =
                settings.tunnelPingTimeoutSeconds?.toMillis()
                    ?: (settings.tunnelPingAttempts * 2000L)
            val pingResult = runCatching {
                networkUtils.pingWithStats(pingTarget, settings.tunnelPingAttempts, timeout)
            }
            val pingStats = pingResult.getOrNull()
            val recovered = pingStats?.isReachable == true || pingStats?.transmitted == 0

            if (recovered) {
                Timber.d(
                    "HandshakeRestartHandler: tunnel $tunnelId recovered after attempt $attempt"
                )
                emitMessage(config.name, BackendMessage.ConnectionRestored)
                // Reset attempt counter but keep totalRestarts visible
                attempt = 0
                updateProgress(tunnelId, TunnelRestartProgress(totalRestarts = totalRestarts))
                pingTarget = awaitPingFailures(tunnelId, settings)
                continue
            }

            // COOLDOWN — only if there are remaining attempts
            if (attempt < maxAttempts) {
                val cooldownMs =
                    computeCooldown(
                        settings.restartCooldownSeconds,
                        attempt,
                        settings.isBackoffEnabled,
                    ) * 1000L
                val cooldownEnd = System.currentTimeMillis() + cooldownMs
                Timber.d(
                    "HandshakeRestartHandler: cooldown ${cooldownMs / 1000}s before next restart for tunnel $tunnelId"
                )
                updateProgress(
                    tunnelId,
                    TunnelRestartProgress(
                        attemptNumber = attempt,
                        maxAttempts = maxAttempts,
                        nextRetryAtMillis = cooldownEnd,
                        reason = BackendMessage.RestartReason.PING_FAILURE,
                        failingPingTargets = listOf(pingTarget),
                        totalRestarts = totalRestarts,
                    ),
                )

                // Race against periodic ping recovery during cooldown
                val recoveredDuringCooldown =
                    withTimeoutOrNull(cooldownMs) {
                        activeTunnels
                            .mapNotNull { it[tunnelId]?.pingStates }
                            .distinctUntilChanged()
                            .first { pingStates ->
                                pingStates.values.isNotEmpty() &&
                                    pingStates.values.all { it.isReachable }
                            }
                        true
                    } != null

                if (recoveredDuringCooldown) {
                    Timber.d("HandshakeRestartHandler: tunnel $tunnelId recovered during cooldown")
                    emitMessage(config.name, BackendMessage.ConnectionRestored)
                    attempt = 0
                    updateProgress(tunnelId, TunnelRestartProgress(totalRestarts = totalRestarts))
                    pingTarget = awaitPingFailures(tunnelId, settings)
                    continue
                }
            }
            // If not recovered: loop continues → next restart attempt
        }
    }

    /**
     * Suspends until [MonitoringSettings.pingFailuresBeforeRestart] consecutive ping cycles all
     * report unreachable. Returns the ping target of the failing peer.
     */
    private suspend fun awaitPingFailures(tunnelId: Int, settings: MonitoringSettings): String {
        var consecutive = 0
        var target = CLOUDFLARE_IPV4_IP

        activeTunnels
            .mapNotNull { it[tunnelId]?.pingStates }
            .distinctUntilChanged()
            .first { pingStates ->
                val allFailing =
                    pingStates.values.isNotEmpty() &&
                        pingStates.values.all {
                            !it.isReachable &&
                                it.transmitted > 0 &&
                                it.failureReason != FailureReason.NoConnectivity
                        }
                if (allFailing) {
                    pingStates.values
                        .firstOrNull { !it.isReachable }
                        ?.pingTarget
                        ?.let { target = it }
                    consecutive++
                } else {
                    consecutive = 0
                }
                consecutive >= settings.pingFailuresBeforeRestart
            }

        return target
    }

    private fun MonitoringSettings.isAutoRestartActive(): Boolean =
        isRestartOnHandshakeTimeoutEnabled && isPingEnabled

    private fun computeCooldown(baseSec: Int, attempt: Int, isBackoff: Boolean): Long =
        if (isBackoff) baseSec.toLong() * (1L shl (attempt - 1).coerceAtMost(MAX_BACKOFF_SHIFT))
        else baseSec.toLong()

    companion object {
        private const val TUNNEL_UP_TIMEOUT_MS = 30_000L
        private const val RESTART_SETTLE_DELAY_MS = 300L
        private const val VERIFY_SETTLE_DELAY_MS = 5_000L
        private const val MAX_BACKOFF_SHIFT = 20 // cap at 2^20 (~12 days with 1s base)
    }
}
