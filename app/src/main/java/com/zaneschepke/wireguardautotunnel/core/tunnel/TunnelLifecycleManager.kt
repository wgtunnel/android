package com.zaneschepke.wireguardautotunnel.core.tunnel

import com.zaneschepke.wireguardautotunnel.core.tunnel.backend.TunnelBackend
import com.zaneschepke.wireguardautotunnel.domain.enums.BackendMode
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelStatus
import com.zaneschepke.wireguardautotunnel.domain.events.BackendCoreException
import com.zaneschepke.wireguardautotunnel.domain.events.BackendMessage
import com.zaneschepke.wireguardautotunnel.domain.events.UnknownError
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.state.LogHealthState
import com.zaneschepke.wireguardautotunnel.domain.state.PingState
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelState
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelStatistics
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class TunnelLifecycleManager(
    private val backend: TunnelBackend,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val sharedActiveTunnels: MutableStateFlow<Map<Int, TunnelState>>,
) : TunnelProvider {

    override val activeTunnels: StateFlow<Map<Int, TunnelState>> = sharedActiveTunnels.asStateFlow()

    private val _errorEvents = MutableSharedFlow<Pair<String?, BackendCoreException>>()
    override val errorEvents: SharedFlow<Pair<String?, BackendCoreException>> =
        _errorEvents.asSharedFlow()

    private val _messageEvents = MutableSharedFlow<Pair<String?, BackendMessage>>()
    override val messageEvents: SharedFlow<Pair<String?, BackendMessage>> =
        _messageEvents.asSharedFlow()

    private val tunnelJobs = ConcurrentHashMap<Int, Job>()
    private val tunMutex = Mutex()
    private val tunStatusMutex = Mutex()

    override suspend fun startTunnel(tunnelConfig: TunnelConfig): Result<Unit> =
        tunMutex.withLock {
            val id = tunnelConfig.id
            if (sharedActiveTunnels.value.containsKey(id)) {
                Timber.w("Tunnel is already running: ${tunnelConfig.name}")
                return Result.failure(IllegalStateException("Tunnel already running"))
            }

            val startupCompleted = CompletableDeferred<Result<Unit>>()

            val job =
                applicationScope.launch(ioDispatcher) {
                    try {
                        updateTunnelStatus(id, TunnelStatus.Starting)
                        backend.tunnelStateFlow(tunnelConfig).collect { status ->
                            updateTunnelStatus(id, status)

                            if (status != TunnelStatus.Starting && !startupCompleted.isCompleted) {
                                if (status is TunnelStatus.Up) {
                                    startupCompleted.complete(Result.success(Unit))
                                } else {
                                    startupCompleted.complete(Result.failure(UnknownError()))
                                }
                            }
                        }
                    } catch (e: BackendCoreException) {
                        _errorEvents.emit(tunnelConfig.name to e)
                        updateTunnelStatus(id, TunnelStatus.Down)
                        startupCompleted.complete(Result.failure(e))
                    } catch (_: CancellationException) {} finally {
                        tunnelJobs.remove(id)
                        sharedActiveTunnels.update { it - id }
                    }
                }

            tunnelJobs[id] = job
            job.invokeOnCompletion { tunnelJobs.remove(id) }

            try {
                startupCompleted.await()
            } catch (e: Throwable) {
                job.cancel()
                Result.failure(e)
            }
        }

    override suspend fun stopTunnel(tunnelId: Int) =
        tunMutex.withLock {
            val currentState = sharedActiveTunnels.value[tunnelId]?.status ?: return@withLock
            updateTunnelStatus(tunnelId, TunnelStatus.Stopping)
            tunnelJobs[tunnelId]?.cancel()

            withTimeoutOrNull(STOP_TIMEOUT_MS) {
                activeTunnels.first {
                    !it.containsKey(tunnelId) || it[tunnelId]!!.status == TunnelStatus.Down
                }
            }
                ?: run {
                    Timber.w("Stop timeout for $tunnelId (was $currentState); forcing kill")
                    forceStopTunnel(tunnelId)
                }
        }

    override suspend fun forceStopTunnel(tunnelId: Int) {
        backend.forceStopTunnel(tunnelId)
        tunnelJobs[tunnelId]?.cancel()
        tunnelJobs.remove(tunnelId)
        sharedActiveTunnels.update { it - tunnelId }
        updateTunnelStatus(tunnelId, TunnelStatus.Down)
    }

    override suspend fun stopActiveTunnels() {
        sharedActiveTunnels.value.forEach { (id, state) ->
            if (state.status.isUpOrStarting()) {
                stopTunnel(id)
            }
        }
    }

    override suspend fun updateTunnelStatus(
        tunnelId: Int,
        status: TunnelStatus?,
        stats: TunnelStatistics?,
        pingStates: Map<String, PingState>?,
        logHealthState: LogHealthState?,
    ) =
        tunStatusMutex.withLock {
            sharedActiveTunnels.update { currentTuns ->
                if (!currentTuns.containsKey(tunnelId) && status != TunnelStatus.Starting) {
                    val hasActiveJob = tunnelJobs.containsKey(tunnelId)
                    if (!hasActiveJob || status == null) {
                        Timber.d("Ignoring update for inactive tunnel $tunnelId")
                        return@update currentTuns
                    }
                }
                val existingState = currentTuns[tunnelId] ?: TunnelState()
                val newStatus = status ?: existingState.status
                if (newStatus == TunnelStatus.Down) {
                    Timber.d("Removing tunnel $tunnelId from activeTunnels as state is DOWN")
                    currentTuns - tunnelId
                } else if (
                    existingState.status == newStatus &&
                        stats == null &&
                        pingStates == null &&
                        logHealthState == null
                ) {
                    Timber.d("Skipping redundant state update for ${tunnelId}: $newStatus")
                    currentTuns
                } else {
                    val updated =
                        existingState.copy(
                            status = newStatus,
                            statistics = stats ?: existingState.statistics,
                            pingStates = pingStates ?: existingState.pingStates,
                            logHealthState = logHealthState ?: existingState.logHealthState,
                        )
                    currentTuns + (tunnelId to updated)
                }
            }
        }

    override fun setBackendMode(backendMode: BackendMode) = backend.setBackendMode(backendMode)

    override fun getBackendMode(): BackendMode = backend.getBackendMode()

    override suspend fun runningTunnelNames(): Set<String> = backend.runningTunnelNames()

    override fun handleDnsReresolve(tunnelConfig: TunnelConfig): Boolean =
        backend.handleDnsReresolve(tunnelConfig)

    override suspend fun forceSocketRebind(tunnelConfig: TunnelConfig): Boolean =
        backend.forceSocketRebind(tunnelConfig)

    override fun getStatistics(tunnelId: Int): TunnelStatistics? = backend.getStatistics(tunnelId)

    companion object {
        const val STOP_TIMEOUT_MS: Long = 5_000L
    }
}
