package com.zaneschepke.tunnel.backend

import com.zaneschepke.networkmonitor.ActiveNetwork
import com.zaneschepke.networkmonitor.StableNetworkEngine
import com.zaneschepke.tunnel.ApplicationProvider
import com.zaneschepke.tunnel.StatusCallback
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.VpnBackend
import com.zaneschepke.tunnel.backend.dns.EndpointResolver
import com.zaneschepke.tunnel.event.TunnelEvent
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.DnsBoostrapMode
import com.zaneschepke.tunnel.model.KillSwitchConfig
import com.zaneschepke.tunnel.service.ServiceManager
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.tunnel.state.BootstrapState
import com.zaneschepke.tunnel.state.EngineStartResult
import com.zaneschepke.tunnel.state.KillSwitchState
import com.zaneschepke.tunnel.util.RootShell
import com.zaneschepke.tunnel.util.RootShellException
import com.zaneschepke.tunnel.util.buildResolvedPeers
import com.zaneschepke.tunnel.util.findEndpointMismatches
import com.zaneschepke.tunnel.util.toHostMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class TunnelBackend(
    private val scope: CoroutineScope,
    override val applicationProvider: ApplicationProvider,
    private val stableNetworkEngine: StableNetworkEngine,
) : Backend {

    private val serviceManager: ServiceManager by inject(ServiceManager::class.java)
    private val engine: TunnelEngine by inject(TunnelEngine::class.java)

    private val _status = MutableStateFlow(BackendStatus())
    override val status: Flow<BackendStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<TunnelEvent>(extraBufferCapacity = 32)
    override val events = _events.asSharedFlow()

    private val tunnelMutex = Mutex()

    private val tunnelJobs = ConcurrentHashMap<Int, Job>()
    private val byHandle = ConcurrentHashMap<Int, Int>()
    private val byTunnelId = ConcurrentHashMap<Int, Int>()
    private val pendingResolutionJobs = ConcurrentHashMap<Int, Job>()

    private val endpointResolver =
        EndpointResolver(
            stableNetworkEngine = stableNetworkEngine,
            getDnsMode = { _status.value.dnsMode },
            isKillSwitchEnabled = { _status.value.killSwitch.enabled },
        )

    private val statusCallback = StatusCallback { handle, code ->
        val state = Tunnel.State.fromNative(code) ?: return@StatusCallback
        val tunnelId = byHandle[handle] ?: return@StatusCallback
        val current = _status.value.activeTunnels[tunnelId]?.transportState
        if (current != state) {
            updateTunnelTransportState(tunnelId, state)
        }
    }

    override suspend fun start(tunnel: Tunnel, mode: BackendMode): Result<Unit> =
        tunnelMutex.withLock {
            runCatching {
                    if (_status.value.activeTunnels.containsKey(tunnel.id)) {
                        Timber.w("Tunnel ${tunnel.id} already running")
                        return@runCatching
                    }

                    val isFirst = _status.value.activeTunnels.isEmpty()

                    addOrReplaceActiveTunnel(
                        tunnel.id,
                        ActiveTunnel(
                            tunnel = tunnel,
                            transportState = Tunnel.State.Starting,
                            mode = mode,
                        ),
                    )
                    applicationProvider.refreshTile(serviceManager.context)

                    val scriptsEnabled = tunnel.scriptsEnabled

                    if (isFirst) VpnBackend.setStatusCallback(statusCallback)

                    if (scriptsEnabled)
                        mode.config.`interface`.preUp?.let { runScripts(it, tunnel.id) }

                    setupServicesAndProtectorForMode(tunnel, mode)

                    if (hasDynamicEndpoints(mode)) {
                        pendingResolutionJobs[tunnel.id] = startTunnelBootstrapJob(tunnel, mode)
                    } else {
                        val result = engine.start(tunnel.id, mode)
                        onEngineStartResult(tunnel.id, result)
                        if (scriptsEnabled) {
                            mode.config.`interface`.postUp?.let { runScripts(it, tunnel.id) }
                        }
                        tunnelJobs[tunnel.id] = startTunnelJobs(tunnel, mode)
                    }
                }
                .onFailure { cleanup(tunnel.id) }
        }

    override suspend fun bounceTunnelDevice(tunnelId: Int) = tunnelMutex.withLock {
        Timber.i("Bouncing tunnel device for tunnel id: $tunnelId...")
        val activeConfig =
            _status.value.activeTunnels[tunnelId]
                ?: return@withLock Timber.w("No active config to bounce")
        val mode = activeConfig.mode ?: return@withLock
        val tunnel = activeConfig.tunnel ?: return@withLock
        val handle =
            byTunnelId[tunnelId] ?: return@withLock Timber.w("Handle missing for bounce config..")
        engine.stop(handle, mode)
        resolveAndStartEngine(tunnel, mode)
        Timber.i("Tunnel device for tunnel $tunnelId bounced successfully.")
    }

    private suspend fun resolveAndStartEngine(tunnel: Tunnel, mode: BackendMode) {
        updateTunnelBootstrapState(tunnel.id, BootstrapState.ResolvingDns)

        val resultMap = endpointResolver.resolvePeers(mode)

        val networkHasIpv6 = stableNetworkEngine.stableState.value?.state?.hasIpv6 ?: false
        val hostMap =
            resultMap.toHostMap(
                preferIpv6 = tunnel.ipStrategy is Tunnel.IpStrategy.PreferIpv6 && networkHasIpv6
            )
        val resolvedPeers = mode.config.buildResolvedPeers(hostMap)

        updateTunnelBootstrapState(tunnel.id, BootstrapState.Complete)

        val resolvedConfig = mode.config.copy(peers = resolvedPeers)
        val updatedMode =
            when (mode) {
                is BackendMode.Vpn -> mode.copy(config = resolvedConfig)
                is BackendMode.Proxy.Standard -> mode.copy(config = resolvedConfig)
                is BackendMode.Proxy.KillSwitchPrimary -> mode.copy(config = resolvedConfig)
            }

        val result = engine.start(tunnel.id, updatedMode)
        onEngineStartResult(tunnel.id, result)
    }

    private fun startTunnelBootstrapJob(tunnel: Tunnel, mode: BackendMode) = scope.launch {
        try {
            resolveAndStartEngine(tunnel, mode)
            val scriptsEnabled = tunnel.scriptsEnabled
            if (scriptsEnabled) {
                mode.config.`interface`.postUp?.let { runScripts(it, tunnel.id) }
            }

            tunnelJobs[tunnel.id] = startTunnelJobs(tunnel, mode)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) {
                Timber.d("Bootstrap job cancelled for tunnel ${tunnel.id}")
            } else {
                Timber.e(t, "Tunnel bootstrap failed for ${tunnel.id}")
                cleanup(tunnel.id)
            }
            if (t is kotlinx.coroutines.CancellationException) throw t
        }
    }

    private suspend fun setupServicesAndProtectorForMode(tunnel: Tunnel, mode: BackendMode) {
        when (mode) {
            is BackendMode.Proxy.KillSwitchPrimary -> {
                val service = serviceManager.ensureVpnReady()
                service.setKillSwitch(mode.killSwitchConfig)
            }
            is BackendMode.Proxy.Standard -> {
                serviceManager.getTunnelService()
            }
            is BackendMode.Vpn -> {
                val service = serviceManager.ensureVpnReady()
                service.createTunInterface(tunnel, mode.config)
            }
        }
    }

    private fun onEngineStartResult(tunnelId: Int, result: EngineStartResult) {
        updateActiveTunnel(tunnelId) {
            it.copy(interfaceName = result.interfaceName, uptime = System.currentTimeMillis())
        }
        byHandle[result.handle] = tunnelId
        byTunnelId[tunnelId] = result.handle
    }

    private suspend fun cleanup(tunnelId: Int) {
        pendingResolutionJobs.remove(tunnelId)?.cancel()
        tunnelJobs.remove(tunnelId)?.cancel()

        val activeTunnels = _status.value.activeTunnels

        val vpnTypeCount = activeTunnels.values.count { it.mode is BackendMode.Vpn }

        val proxyTypeCount = activeTunnels.values.count { it.mode is BackendMode.Proxy.Standard }

        removeActiveTunnel(tunnelId)
        byTunnelId[tunnelId]?.let { byHandle.remove(it) }
        byTunnelId.remove(tunnelId)

        if (vpnTypeCount == 1 && !_status.value.killSwitch.enabled) {
            serviceManager.ensureVpnShutdown()
        }
        if (proxyTypeCount == 1) {
            serviceManager.stopTunnelService()
        }
    }

    private suspend fun runScripts(commands: List<String>, tunnelId: Int) {
        try {
            commands.forEach { cmd ->
                withTimeout(3_000.milliseconds) {
                    withContext(Dispatchers.IO) { RootShell.run(cmd) }
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "Root shell commands failed")
            if (t is RootShellException.NoRootAccess) {
                _events.emit(TunnelEvent.NoRootShellAccess(tunnelId = tunnelId))
            }
        }
    }

    override fun setAlwaysOnCallback(alwaysOnCallback: VpnService.AlwaysOnCallback) {
        ServiceManager.alwaysOnCallback = alwaysOnCallback
    }

    override suspend fun stop(id: Int): Result<Unit> = tunnelMutex.withLock {
        runCatching {
            val activeTun = _status.value.activeTunnels[id] ?: return@runCatching
            updateTunnelTransportState(id, Tunnel.State.Stopping)

            try {
                stopTunnelInternal(id, activeTun)
            } finally {
                applicationProvider.refreshTile(serviceManager.context)
                if (_status.value.activeTunnels.isEmpty()) {
                    VpnBackend.setStatusCallback(null)
                }
            }
        }
    }

    private suspend fun stopTunnelInternal(tunnelId: Int, activeTunnel: ActiveTunnel) {
        updateTunnelTransportState(tunnelId, Tunnel.State.Stopping)

        val handle = byTunnelId[tunnelId]

        if (handle == null) {
            cleanup(tunnelId)
            return
        }

        val scriptsEnabled = activeTunnel.tunnel?.scriptsEnabled == true
        val mode = activeTunnel.mode ?: return

        try {
            if (scriptsEnabled) mode.config.`interface`.preDown?.let { runScripts(it, tunnelId) }
            engine.stop(handle, activeTunnel.mode)
            if (scriptsEnabled) mode.config.`interface`.postDown?.let { runScripts(it, tunnelId) }
        } finally {
            cleanup(tunnelId)
        }
    }

    override suspend fun setKillSwitch(config: KillSwitchConfig) = runCatching {
        val service = serviceManager.getVpnService()
        service.setKillSwitch(config)
        _status.update { current ->
            current.copy(killSwitch = current.killSwitch.copy(enabled = true, config = config))
        }
    }

    override suspend fun disableKillSwitch() = runCatching {
        val service = serviceManager.getVpnService()
        service.setKillSwitch(null)
        _status.update { current ->
            current.copy(
                killSwitch =
                    KillSwitchState(
                        enabled = false,
                        config = null,
                        primaryTunnel = current.killSwitch.primaryTunnel,
                    )
            )
        }
    }

    override suspend fun setBootstrapDnsMode(mode: DnsBoostrapMode) {
        _status.update { it.copy(dnsMode = mode) }
        Timber.d("DNS Bootstrap mode set to: $mode")
    }

    override suspend fun stopAllActiveTunnels() = tunnelMutex.withLock {
        _status.value.activeTunnels.forEach { (id, tunnel) -> stopTunnelInternal(id, tunnel) }
        applicationProvider.refreshTile(serviceManager.context)
        VpnBackend.setStatusCallback(null)
        serviceManager.stopTunnelService()
        if (!_status.value.killSwitch.enabled) {
            serviceManager.stopVpnService()
            serviceManager.stopCompanionService()
        }
        Result.success(Unit)
    }

    private fun hasDynamicEndpoints(mode: BackendMode): Boolean {
        return mode.config.peers.any { !it.isStaticallyConfigured && it.endpoint != null }
    }

    private fun updateStatus(transform: (BackendStatus) -> BackendStatus) {
        _status.update(transform)
    }

    fun addOrReplaceActiveTunnel(id: Int, tunnel: ActiveTunnel) {
        updateStatus { current ->
            current.copy(activeTunnels = current.activeTunnels + (id to tunnel))
        }
    }

    fun updateActiveTunnel(id: Int, transform: (ActiveTunnel) -> ActiveTunnel) {
        updateStatus { current ->
            val existing = current.activeTunnels[id] ?: return@updateStatus current
            current.copy(activeTunnels = current.activeTunnels + (id to transform(existing)))
        }
    }

    fun removeActiveTunnel(id: Int) {
        updateStatus { current -> current.copy(activeTunnels = current.activeTunnels - id) }
    }

    fun updateTunnelTransportState(id: Int, newState: Tunnel.State) {
        updateActiveTunnel(id) { tunnel ->
            val stateChanged = tunnel.transportState != newState
            tunnel.copy(
                transportState = newState,
                lastHealthChangeMs =
                    if (stateChanged || tunnel.lastHealthChangeMs == 0L) {
                        System.currentTimeMillis()
                    } else {
                        tunnel.lastHealthChangeMs
                    },
            )
        }
    }

    fun updateTunnelBootstrapState(id: Int, newState: BootstrapState) {
        updateActiveTunnel(id) { tunnel -> tunnel.copy(bootstrapState = newState) }
    }

    private fun startTunnelJobs(tunnel: Tunnel, mode: BackendMode): Job {
        return scope.launch {
            supervisorScope {
                val isNotStaticConfig = mode.config.peers.any { !it.isStaticallyConfigured }
                if (isNotStaticConfig) {
                    when (val strategy = tunnel.ipStrategy) {
                        Tunnel.IpStrategy.Ipv4Only -> Unit
                        is Tunnel.IpStrategy.PreferIpv6 -> {
                            if (strategy.recoveryEnabled) startIpv6RecoveryJob(tunnel.id, mode)
                        }
                    }
                }

                tunnel.features.forEach { feature ->
                    when (feature) {
                        is Tunnel.Feature.ActiveConfigMonitor -> {
                            startActiveConfigJob(tunnel.id, mode, feature.intervalSeconds)
                        }
                        Tunnel.Feature.SeamlessRecovery -> startSeamlessRecoveryJob(tunnel.id)
                    }
                }

                awaitCancellation()
            }
        }
    }

    private fun CoroutineScope.startActiveConfigJob(
        tunnelId: Int,
        mode: BackendMode,
        interval: Int,
    ) = launch {
        while (isActive) {
            val handle =
                byTunnelId[tunnelId]
                    ?: run {
                        Timber.w("Failed to find tunnel handle, skipping stats")
                        continue
                    }
            val activeConfig = engine.getActiveConfig(handle, mode)
            updateActiveTunnel(tunnelId) { it.copy(activeConfig = activeConfig) }
            delay(interval.seconds)
        }
    }

    /*
    Seamless recovery now covers all the use cases for DDNS, IPv4 fallback, NAT issues, Mimic packet issues, etc.
    Since we now dup the vpn service fd and don't like WG close it, we can safely stop (close) the tunnel device via
    bounceTunnelDevice and start it up again with a fresh DNS query that will solve DDNS and IPv4 fallback. Due to the fresh
    tunnel device, we take a nuclear approach to fixing any other issues that could also occur at the same time (like NAT or
    Amnezia mimic packets issues or network migration issues)
    */
    private fun CoroutineScope.startSeamlessRecoveryJob(tunnelId: Int) = launch {
        val shouldRecoverFlow =
            combine(
                    status.mapNotNull { it.activeTunnels[tunnelId]?.transportState },
                    stableNetworkEngine.stableState.filterNotNull(),
                ) { tunnelState, networkState ->
                    val isHandshakeFailure = tunnelState is Tunnel.State.Up.HandshakeFailure
                    val isNetworkConnected =
                        networkState.state.activeNetwork !is ActiveNetwork.Disconnected

                    isHandshakeFailure && isNetworkConnected
                }
                .distinctUntilChanged()

        while (isActive) {
            Timber.i("Advanced recovery: waiting for recovery conditions to be met")
            shouldRecoverFlow.first { it }

            if (!isActive) break

            Timber.i(
                "Advanced recovery: entered HandshakeFailure while network connected. Waiting for tunnel to stabilize..."
            )
            delay(TUNNEL_HEALTH_STABILIZE_WINDOW_MILLIS.milliseconds)

            // get fresh snapshots
            val currentState = _status.value.activeTunnels[tunnelId]?.transportState
            val isNetworkStillConnected =
                stableNetworkEngine.stableState.value?.state?.activeNetwork !is
                    ActiveNetwork.Disconnected

            if (currentState is Tunnel.State.Up.HandshakeFailure && isNetworkStillConnected) {
                Timber.i(
                    "Advanced recovery: bouncing tunnel $tunnelId after persistent handshake failure"
                )
                bounceTunnelDevice(tunnelId)

                delay(TUNNEL_RECOVERY_COOLDOWN_MILLIS.milliseconds)
            }
        }
    }

    /*
    Runs once per network where IPv6 is available while state is healthy and checks the current
    active WG config to see if we have any endpoints that aren't IPv6 that should be upgraded to IPv6
    and upgrades them via WG's UAPI with a peer update request
    */
    private fun CoroutineScope.startIpv6RecoveryJob(tunnelId: Int, mode: BackendMode) = launch {
        val ipv6RecoveryTrigger =
            combine(
                    stableNetworkEngine.stableState.filterNotNull(),
                    status.mapNotNull { it.activeTunnels[tunnelId] },
                ) { networkState, tunnel ->
                    val activeNetworkKey = networkState.state.activeNetwork.key()
                    val hasIpv6 = networkState.state.hasIpv6
                    val isHealthy = tunnel.transportState is Tunnel.State.Up.Healthy

                    Triple(activeNetworkKey, hasIpv6, isHealthy)
                }
                // Only run once per distinct network and IPv6 presence
                .distinctUntilChangedBy { it.first + it.second }
                // Only emit when recovery conditions met
                .filter { (_, hasIpv6, isHealthy) -> hasIpv6 && isHealthy }

        while (isActive) {
            Timber.i("Ipv6 Recovery: waiting for recovery conditions to be met")
            ipv6RecoveryTrigger.first()

            if (!isActive) break

            Timber.d("Ipv6 Recovery: conditions met, waiting for tunnel to stabilize...")
            delay(TUNNEL_HEALTH_STABILIZE_WINDOW_MILLIS.milliseconds)

            // recheck
            val currentNetworkState = stableNetworkEngine.stableState.value?.state
            val currentTunnel = _status.value.activeTunnels[tunnelId]

            val stillHealthy = currentTunnel?.transportState is Tunnel.State.Up.Healthy
            val stillHasIpv6 = currentNetworkState?.hasIpv6 == true

            if (!stillHealthy || !stillHasIpv6) {
                Timber.d(
                    "Aborting IPv6 recovery: tunnel lost healthy or network support during stabilization"
                )
                continue
            }

            // 5. Safe to proceed with procedural recovery logic
            val activeConfig =
                try {
                    val handle =
                        byTunnelId[tunnelId]
                            ?: run {
                                Timber.w(
                                    "Failed to find tunnel handle, Ipv6 recovery cannot complete"
                                )
                                continue
                            }
                    engine.getActiveConfig(handle, mode)
                } catch (t: Throwable) {
                    Timber.w(t, "UAPI query failed during peer reconciliation")
                    continue
                } ?: continue

            // Quick check to avoid DNS call if not needed
            if (
                activeConfig.peers.all { it.endpoint == null || it.endpoint?.contains("[") == true }
            ) {
                Timber.i("Ipv6 Recovery: All peers are already IPv6 or empty endpoints")
                continue
            }

            val results = endpointResolver.resolvePeers(mode)
            if (results.isEmpty()) continue

            val mismatches = activeConfig.findEndpointMismatches(results, true)

            if (mismatches.isNotEmpty()) {
                Timber.i(
                    "Ipv6 Recovery: found endpoint mismatches, updating tunnel with Ipv6 endpoints"
                )
                val resolvedPeers = mode.config.buildResolvedPeers(mismatches)
                val handle =
                    byTunnelId[tunnelId]
                        ?: run {
                            Timber.w(
                                "Failed to find tunnel handle, recovery to ipv6 peers cannot complete"
                            )
                            continue
                        }
                engine.updatePeers(handle, mode, resolvedPeers)
                _events.emit(TunnelEvent.RecoveredToIpv6(tunnelId))
            } else {
                Timber.i(
                    "Ipv6 Recovery: No mismatches found for tunnel Ipv6 endpoints, no recovery necessary"
                )
            }
        }
    }

    companion object {
        private const val TUNNEL_HEALTH_STABILIZE_WINDOW_MILLIS = 8_000L
        private const val TUNNEL_RECOVERY_COOLDOWN_MILLIS = 30_000L
    }
}
