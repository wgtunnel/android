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
import com.zaneschepke.tunnel.model.DnsBootstrapResult
import com.zaneschepke.tunnel.model.Host
import com.zaneschepke.tunnel.model.KillSwitchConfig
import com.zaneschepke.tunnel.model.PublicKey
import com.zaneschepke.tunnel.service.ServiceHolder
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.tunnel.state.BootstrapState
import com.zaneschepke.tunnel.state.EngineStartResult
import com.zaneschepke.tunnel.state.KillSwitchState
import com.zaneschepke.tunnel.util.RootShell
import com.zaneschepke.tunnel.util.RootShellException
import com.zaneschepke.tunnel.util.buildResolvedPeers
import com.zaneschepke.tunnel.util.toHostMap
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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

    private val serviceHolder: ServiceHolder by inject(ServiceHolder::class.java)
    private val engine: TunnelEngine by inject(TunnelEngine::class.java)

    private val _status = MutableStateFlow(BackendStatus())
    override val status: Flow<BackendStatus> = _status.asStateFlow()

    private val _isSeamlessRoamingEnabled = MutableStateFlow(false)
    override val isSeamlessRoamingEnabled: Boolean
        get() = _isSeamlessRoamingEnabled.value

    private val _events = MutableSharedFlow<TunnelEvent>(extraBufferCapacity = 32)
    override val events = _events.asSharedFlow()

    private val tunnelMutex = Mutex()

    private val tunnelJobs = ConcurrentHashMap<Int, Job>()
    private val byHandle = ConcurrentHashMap<Int, Int>()
    private val byTunnelId = ConcurrentHashMap<Int, Int>()
    private val peerUpdateMutexes = ConcurrentHashMap<Int, Mutex>()
    private val pendingResolutionJobs = ConcurrentHashMap<Int, Job>()
    private var seamlessRoamingJob: Job? = null

    private val endpointResolver =
        EndpointResolver(
            stableNetworkEngine = stableNetworkEngine,
            getDnsMode = { _status.value.dnsMode },
            isKillSwitchEnabled = { _status.value.killSwitch.enabled },
        )

    enum class PeerUpdateReason {
        DDNS_CHECK,
        IPV4_FALLBACK,
        IPV6_RECOVERY,
        NETWORK_CHANGE_RESET,
    }

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
                    applicationProvider.refreshTile(serviceHolder.context)

                    val scriptsEnabled = tunnel.scriptsEnabled

                    if (isFirst) VpnBackend.setStatusCallback(statusCallback)

                    if (scriptsEnabled)
                        mode.config.`interface`.preUp?.let { runScripts(it, tunnel.id) }

                    setupServiceForMode(tunnel, mode)

                    if (hasDynamicEndpoints(mode)) {
                        pendingResolutionJobs[tunnel.id] = startTunnelBootstrapJob(tunnel, mode)
                    } else {
                        val result = engine.start(tunnel, mode)
                        onEngineStartResult(tunnel.id, result)

                        if (scriptsEnabled) {
                            mode.config.`interface`.postUp?.let { runScripts(it, tunnel.id) }
                        }
                        tunnelJobs[tunnel.id] = startTunnelJobs(result.handle, tunnel, mode)
                    }
                }
                .onFailure { cleanup(tunnel.id) }
        }

    @OptIn(FlowPreview::class)
    override suspend fun setSeamlessRoaming(enabled: Boolean): Result<Unit> = runCatching {
        if (_isSeamlessRoamingEnabled.value == enabled) return@runCatching
        _isSeamlessRoamingEnabled.value = enabled
        seamlessRoamingJob?.cancel()
        seamlessRoamingJob = null

        if (enabled) {
            seamlessRoamingJob = scope.launch {
                stableNetworkEngine.stableState
                    .distinctUntilChangedBy { stable ->
                        when (val net = stable?.state?.activeNetwork) {
                            is ActiveNetwork.Wifi -> {
                                // WiFi uses key with linkProperties and optional BSSID detection so
                                // AP migration is detected
                                "${net.key(true)}:${net.linkProperties}"
                            }
                            else -> net?.key()
                        }
                    }
                    .map { it?.state?.activeNetwork }
                    .collectLatest { network ->
                        delay(700.milliseconds) // give network time to stabilize
                        if (
                            network != null &&
                                network !is ActiveNetwork.Disconnected &&
                                _status.value.activeTunnels.isNotEmpty()
                        ) {
                            Timber.i(
                                "Seamless Roaming: Network changed to ${network.key()}, updating bind on active tunnels"
                            )
                            updateBindForActiveTunnels()
                        }
                    }
            }
        } else {
            Timber.i("Seamless Roaming disabled, rebinding to remove network bind")
            updateBindForActiveTunnels()
        }
    }

    private suspend fun updateBindForActiveTunnels() {
        // Take a snapshot to avoid race
        val currentTunnels = _status.value.activeTunnels.toMap()

        currentTunnels.forEach { (id, activeTunnel) ->
            val handle = byTunnelId[id] ?: return@forEach
            val mode = activeTunnel.mode ?: return@forEach

            try {
                engine.updateBind(handle, mode)
            } catch (t: Throwable) {
                Timber.w(t, "Failed to update bind for tunnel $id during bulk update")
            }
        }
    }

    private fun startTunnelBootstrapJob(tunnel: Tunnel, mode: BackendMode) = scope.launch {
        try {
            updateTunnelBootstrapState(tunnel.id, BootstrapState.ResolvingDns)

            val resultMap = endpointResolver.resolvePeers(mode)
            ensureActive()

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

            val result = engine.start(tunnel, updatedMode)
            onEngineStartResult(tunnel.id, result)

            val scriptsEnabled = tunnel.scriptsEnabled
            if (scriptsEnabled) {
                mode.config.`interface`.postUp?.let { runScripts(it, tunnel.id) }
            }

            tunnelJobs[tunnel.id] = startTunnelJobs(result.handle, tunnel, mode)
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

    private suspend fun setupServiceForMode(tunnel: Tunnel, mode: BackendMode) {
        when (mode) {
            is BackendMode.Proxy.KillSwitchPrimary -> {
                val service = serviceHolder.ensureVpnProtectorRegistered()
                service.setKillSwitch(mode.killSwitchConfig)
            }
            is BackendMode.Proxy.Standard -> {
                serviceHolder.getTunnelService()
            }
            is BackendMode.Vpn -> {
                val service = serviceHolder.ensureVpnProtectorRegistered()
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
        peerUpdateMutexes.remove(tunnelId)

        if (vpnTypeCount == 1 && !_status.value.killSwitch.enabled) {
            serviceHolder.stopVpnService()
        }
        if (proxyTypeCount == 1) {
            serviceHolder.stopTunnelService()
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
        ServiceHolder.alwaysOnCallback = alwaysOnCallback
    }

    override suspend fun stop(id: Int): Result<Unit> = tunnelMutex.withLock {
        runCatching {
            val activeTun = _status.value.activeTunnels[id] ?: return@runCatching
            updateTunnelTransportState(id, Tunnel.State.Stopping)

            try {
                stopTunnelInternal(id, activeTun)
            } finally {
                applicationProvider.refreshTile(serviceHolder.context)
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
        val service = serviceHolder.getVpnService()
        service.setKillSwitch(config)
        _status.update { current ->
            current.copy(killSwitch = current.killSwitch.copy(enabled = true, config = config))
        }
    }

    override suspend fun disableKillSwitch() = runCatching {
        val service = serviceHolder.getVpnService()
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
        applicationProvider.refreshTile(serviceHolder.context)
        VpnBackend.setStatusCallback(null)
        serviceHolder.stopTunnelService()
        if (!_status.value.killSwitch.enabled) {
            serviceHolder.stopVpnService()
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
                lastStateChangeMs =
                    if (stateChanged) {
                        System.currentTimeMillis()
                    } else {
                        tunnel.lastStateChangeMs
                    },
                lastHealthChangeMs =
                    if (newState is Tunnel.State.Up.Healthy) {
                        if (stateChanged || tunnel.lastHealthChangeMs == 0L)
                            System.currentTimeMillis()
                        else tunnel.lastHealthChangeMs
                    } else {
                        tunnel.lastHealthChangeMs
                    },
            )
        }
    }

    fun updateTunnelBootstrapState(id: Int, newState: BootstrapState) {
        updateActiveTunnel(id) { tunnel -> tunnel.copy(bootstrapState = newState) }
    }

    private fun startTunnelJobs(handle: Int, tunnel: Tunnel, mode: BackendMode): Job {
        return scope.launch {
            supervisorScope {
                val isNotStaticConfig = mode.config.peers.any { !it.isStaticallyConfigured }
                if (isNotStaticConfig) {
                    when (val strategy = tunnel.ipStrategy) {
                        Tunnel.IpStrategy.Ipv4Only -> Unit

                        is Tunnel.IpStrategy.PreferIpv6 -> {
                            if (strategy.recoveryEnabled || strategy.fallbackToIpv4Enabled) {
                                startIpv6Job(handle, tunnel.id, strategy)
                            }
                        }
                    }
                }

                tunnel.features.forEach { feature ->
                    when (feature) {
                        is Tunnel.Feature.ActiveConfigMonitor -> {
                            startActiveConfigJob(handle, tunnel.id, mode, feature.intervalSeconds)
                        }
                        Tunnel.Feature.DynamicDNS -> {
                            if (isNotStaticConfig) startDynamicDnsJob(handle, tunnel.id)
                        }
                    }
                }

                awaitCancellation()
            }
        }
    }

    private fun CoroutineScope.startActiveConfigJob(
        handle: Int,
        tunnelId: Int,
        mode: BackendMode,
        interval: Int,
    ) = launch {
        while (isActive) {
            val activeConfig = engine.getActiveConfig(handle, mode)
            updateActiveTunnel(tunnelId) { it.copy(activeConfig = activeConfig) }
            delay(interval.seconds)
        }
    }

    private fun CoroutineScope.startDynamicDnsJob(handle: Int, tunnelId: Int) = launch {
        status
            .mapNotNull { it.activeTunnels[tunnelId]?.transportState }
            .map { it is Tunnel.State.Up.HandshakeFailure }
            .distinctUntilChanged()
            .collectLatest { isFailing ->
                if (!isFailing) return@collectLatest

                delay(DDNS_FAILURE_WINDOW.milliseconds)

                while (isActive) {
                    val stable = stableNetworkEngine.stableState.value
                    if (stable?.state?.hasActiveNetwork() == true) {
                        val tunnel = _status.value.activeTunnels[tunnelId] ?: continue
                        tunnel.mode?.let { mode ->
                            reconcilePeers(tunnelId, handle, mode, PeerUpdateReason.DDNS_CHECK)
                        }
                    }
                    delay(DDNS_MIN_CHECK_INTERVAL.milliseconds)
                }
            }
    }

    private fun findEndpointMismatches(
        freshDns: Map<PublicKey, DnsBootstrapResult>,
        activeConfig: ActiveConfig,
        preferIpv6: Boolean,
    ): Map<PublicKey, Host> {
        val currentEndpoints = activeConfig.peers.associateBy { it.publicKey }

        return freshDns
            .mapNotNull { (pubKey, dnsResult) ->
                val current = currentEndpoints[pubKey] ?: return@mapNotNull null
                val currentHost = current.host ?: return@mapNotNull null

                val freshAddress =
                    if (preferIpv6 && dnsResult.ipv6.isNotEmpty()) {
                        dnsResult.ipv6.first()
                    } else {
                        dnsResult.ipv4.firstOrNull() ?: dnsResult.ipv6.firstOrNull()
                    } ?: return@mapNotNull null

                if (freshAddress != currentHost) {
                    pubKey to freshAddress
                } else {
                    null
                }
            }
            .toMap()
    }

    private fun CoroutineScope.startIpv6Job(
        handle: Int,
        tunnelId: Int,
        strategy: Tunnel.IpStrategy.PreferIpv6,
    ) = launch {
        var currentNetworkKey: String? = null
        var hasRecoveredOnThisNetwork = false
        var hasFallenBackOnThisNetwork = false
        var healthySinceMs: Long? = null
        var failureCount = 0
        var firstFailureTime = 0L
        var ipv6Bad = false

        combine(
                stableNetworkEngine.stableState.filterNotNull(),
                status.mapNotNull { it.activeTunnels[tunnelId] },
            ) { stable, activeTunnel ->
                stable to activeTunnel
            }
            .collect { (stable, activeTunnel) ->
                val newKey = stable.key
                val mode = activeTunnel.mode ?: return@collect

                // Reset state upon network transition
                if (newKey != currentNetworkKey) {
                    // Capture the old state before we reset local variables
                    val wasFallenBack = activeTunnel.isFallenBackToIpv4ForNetwork

                    currentNetworkKey = newKey
                    hasRecoveredOnThisNetwork = false
                    hasFallenBackOnThisNetwork = false
                    healthySinceMs = null
                    failureCount = 0
                    firstFailureTime = 0L
                    ipv6Bad = false

                    Timber.d("Stable network changed resetting IPv6 state ($newKey)")

                    // Optimistically revert to IPv6 on a new network
                    if (wasFallenBack) {
                        Timber.d(
                            "Network changed while in IPv4 fallback. Attempting optimistic reset."
                        )
                        reconcilePeers(
                            tunnelId = tunnelId,
                            handle = handle,
                            mode = mode,
                            reason = PeerUpdateReason.NETWORK_CHANGE_RESET,
                        )
                    }
                }

                val now = System.currentTimeMillis()

                // Determine if currently using IPv6
                val isUsingIpv6 =
                    activeTunnel.activeConfig?.peers?.any { it.endpoint?.startsWith("[") == true }
                        ?: mode.config.peers.any { it.endpoint?.startsWith("[") == true }

                val isHealthy = activeTunnel.transportState is Tunnel.State.Up.Healthy
                val isHandshakeFailure =
                    activeTunnel.transportState is Tunnel.State.Up.HandshakeFailure

                healthySinceMs = if (isHealthy) healthySinceMs ?: now else null
                val healthyDuration = healthySinceMs?.let { now - it } ?: 0L

                if (!isHandshakeFailure) {
                    failureCount = 0
                    firstFailureTime = 0L
                }

                // Fallback
                if (
                    strategy.fallbackToIpv4Enabled &&
                        isHandshakeFailure &&
                        isUsingIpv6 &&
                        !hasFallenBackOnThisNetwork
                ) {
                    if (failureCount == 0) firstFailureTime = now
                    failureCount++

                    val failureDuration = now - firstFailureTime

                    Timber.d(
                        "IPv6 strategy | Fallback check: failureCount=$failureCount duration=${failureDuration}ms"
                    )

                    if (
                        failureCount >= IPV4_FALLBACK_FAILURE_COUNT &&
                            failureDuration >= IPV4_FALLBACK_FAILURE_DURATION
                    ) {
                        hasFallenBackOnThisNetwork = true
                        ipv6Bad = true

                        Timber.d("Fallback to IPv4 triggered on $newKey (marking IPv6 bad)")

                        reconcilePeers(
                            tunnelId = tunnelId,
                            handle = handle,
                            mode = mode,
                            reason = PeerUpdateReason.IPV4_FALLBACK,
                        )
                    }
                }

                // Recovery
                if (
                    strategy.recoveryEnabled &&
                        !isUsingIpv6 &&
                        !hasRecoveredOnThisNetwork &&
                        healthySinceMs != null &&
                        stable.state.hasIpv6 &&
                        !ipv6Bad
                ) {
                    Timber.d(
                        "IPv6 strategy | Recovery check: healthy for ${healthyDuration}ms (need >= ${RECOVERY_STABILITY_WINDOW}ms)"
                    )

                    if (healthyDuration >= RECOVERY_STABILITY_WINDOW) {
                        hasRecoveredOnThisNetwork = true

                        Timber.d("Recovered to IPv6 on $newKey (healthy for ${healthyDuration}ms)")

                        reconcilePeers(
                            tunnelId = tunnelId,
                            handle = handle,
                            mode = mode,
                            reason = PeerUpdateReason.IPV6_RECOVERY,
                        )
                    }
                }
            }
    }

    fun markPeerUpdate(id: Int) {
        updateActiveTunnel(id) { tunnel ->
            tunnel.copy(lastPeerUpdateMs = System.currentTimeMillis())
        }
    }

    private suspend fun reconcilePeers(
        tunnelId: Int,
        handle: Int,
        mode: BackendMode,
        reason: PeerUpdateReason,
    ) {
        val mutex = peerUpdateMutexes.getOrPut(tunnelId) { Mutex() }
        mutex.withLock {
            when (reason) {
                PeerUpdateReason.IPV4_FALLBACK -> {
                    updateActiveTunnel(tunnelId) { it.copy(isFallenBackToIpv4ForNetwork = true) }
                }
                PeerUpdateReason.IPV6_RECOVERY,
                PeerUpdateReason.NETWORK_CHANGE_RESET -> {
                    updateActiveTunnel(tunnelId) { it.copy(isFallenBackToIpv4ForNetwork = false) }
                    if (reason == PeerUpdateReason.NETWORK_CHANGE_RESET) return
                }
                PeerUpdateReason.DDNS_CHECK -> Unit
            }

            val updatedActiveTunnel = _status.value.activeTunnels[tunnelId] ?: return
            val tunnel = updatedActiveTunnel.tunnel ?: return

            val results = endpointResolver.resolvePeers(mode)
            if (results.isEmpty()) return

            val networkHasIpv6 = stableNetworkEngine.stableState.value?.state?.hasIpv6 == true
            val preferIpv6 =
                tunnel.ipStrategy is Tunnel.IpStrategy.PreferIpv6 &&
                    networkHasIpv6 &&
                    !updatedActiveTunnel.isFallenBackToIpv4ForNetwork

            val activeConfig =
                try {
                    engine.getActiveConfig(handle, mode)
                } catch (t: Throwable) {
                    Timber.w(t, "UAPI query failed during peer reconciliation")
                    return
                } ?: return

            val mismatches = findEndpointMismatches(results, activeConfig, preferIpv6)

            Timber.d("Reconciliation complete for $reason. Mismatches found: ${mismatches.size}")

            if (mismatches.isNotEmpty()) {
                Timber.i("Updating peers due to $reason: $mismatches")

                val resolvedPeers = mode.config.buildResolvedPeers(mismatches)

                try {
                    engine.updatePeers(handle, mode, resolvedPeers)
                    markPeerUpdate(tunnelId)

                    when (reason) {
                        PeerUpdateReason.IPV4_FALLBACK ->
                            _events.emit(TunnelEvent.FallbackToIpv4(tunnelId))
                        PeerUpdateReason.IPV6_RECOVERY ->
                            _events.emit(TunnelEvent.RecoveredToIpv6(tunnelId))
                        PeerUpdateReason.DDNS_CHECK ->
                            _events.emit(
                                TunnelEvent.DynamicDnsUpdate(tunnelId, mismatches.keys.toList())
                            )
                        PeerUpdateReason.NETWORK_CHANGE_RESET -> Unit
                    }
                } catch (t: Throwable) {
                    Timber.e(t, "Failed to apply peer updates to WireGuard engine")
                }
            } else {
                Timber.d("No mismatches found, skipping event emission.")
            }
        }
    }

    companion object {
        private const val DDNS_MIN_CHECK_INTERVAL = 30_000L
        private const val DDNS_FAILURE_WINDOW = 15_000L
        private const val IPV4_FALLBACK_FAILURE_COUNT = 4
        private const val IPV4_FALLBACK_FAILURE_DURATION = 10_000L
        private const val RECOVERY_STABILITY_WINDOW = 5_000L
    }
}
