package com.zaneschepke.tunnel.backend

import android.content.Context
import android.content.Intent
import com.getkeepsafe.relinker.ReLinker
import com.zaneschepke.networkmonitor.ActiveNetwork
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.networkmonitor.PrivateDnsMode
import com.zaneschepke.tunnel.*
import com.zaneschepke.tunnel.features.ActiveConfigFeature
import com.zaneschepke.tunnel.model.*
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.tunnel.state.KillSwitchState
import com.zaneschepke.tunnel.state.TunnelRuntimeState
import com.zaneschepke.tunnel.state.TunnelStatus
import com.zaneschepke.tunnel.util.BackendException
import com.zaneschepke.tunnel.util.RootShellException
import com.zaneschepke.tunnel.util.exponentialBackoffForever
import com.zaneschepke.tunnel.util.key
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.collections.filter
import kotlin.collections.firstOrNull
import kotlin.collections.map
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class TunnelBackend(private val context: Context) : Backend {
    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val statusChannel = Channel<TunnelStatus>(Channel.BUFFERED)

    @OptIn(ExperimentalAtomicApi::class)
    private val statusCallbackRegistered = AtomicBoolean(false)
    private var dnsConfigJob : Job? = null

    private val rootShell = RootShell(context)

    private val statusCallback = StatusCallback { handle, interfaceName, statusCode ->
        Timber.d("Native Callback - Handle: $handle, Interface: $interfaceName, Code: $statusCode")
        statusChannel.trySend(TunnelStatus(handle, interfaceName, statusCode))
    }

    private val tunnelMutex = Mutex()
    private val runtimeStates = ConcurrentHashMap<Int, TunnelRuntimeState>()
    private val tunnelLocks = ConcurrentHashMap<Int, Mutex>()
    private val handleIndex = ConcurrentHashMap<Int, Int>() // handle to tunnelId

    private val uapiPath = context.dataDir.absolutePath;

    init {
        ReLinker.loadLibrary(context, "am-go")

        backendScope.launch {
            statusChannel.consumeAsFlow().collect { tunnelStatus ->

                val tunnelState = tunnelStatus.asTunnelState()

                val tunnelId = handleIndex[tunnelStatus.handle] ?: return@collect

                modifyTunnel(tunnelId) { runtime ->

                    val updatedActive = runtime.active.copy(
                        state = tunnelState,
                        lastStateChangeMs = System.currentTimeMillis()
                    )

                    val updatedRuntime = runtime.copy(
                        active = updatedActive
                    )

                    Timber.d(
                        "Tunnel ${runtime.running.tunnel.name} status update: " +
                                "$tunnelStatus -> $tunnelState"
                    )

                    // keep domain model consistent
                    runtime.running.tunnel.updateState(tunnelState)

                    updatedRuntime
                }
            }
        }
    }

    private val _status = MutableStateFlow(BackendStatus())
    override val status: Flow<BackendStatus> = _status.asStateFlow()

    private val networkMonitor = AndroidNetworkMonitor(
        context, object : AndroidNetworkMonitor.ConfigurationListener {
            override val detectionMethod: Flow<AndroidNetworkMonitor.WifiDetectionMethod>
                get() = flowOf(AndroidNetworkMonitor.WifiDetectionMethod.DEFAULT)

            override fun runRootShellCommand(vararg cmd: String): String? {
                // don't need Wi-Fi names
                return null
            }

        },
        applicationScope = backendScope
    )

    private fun getActiveConfigInner(runningTunnel: RunningTunnel) : String? {
        return when(runningTunnel.mode) {
            is BackendMode.Proxy.Standard, is BackendMode.Proxy.KillSwitchPrimary -> ProxyBackend.awgGetProxyConfig(runningTunnel.handle)
            is BackendMode.Vpn -> VpnBackend.awgGetConfig(runningTunnel.handle)
        }
    }

    override suspend fun getActiveConfig(id: Int): Result<ActiveConfig?> {
        val runtime = getRuntimeState(id)

        val activeConfig = runtime?.running?.let { running ->
            getActiveConfigInner(running)?.let { activeIpcConfig ->
                ActiveConfig.parseFromIpc(activeIpcConfig)
            }
        }

        return Result.success(activeConfig)
    }

    override suspend fun disableKillSwitch() = runCatching {
        if (!vpnService.isDone) return@runCatching
        context.stopService(Intent(context, VpnService::class.java))
        resetKillSwitchState()
    }

    private fun resetKillSwitchState() {
        _status.update { current ->
            current.copy(
                killSwitch = KillSwitchState()
            )
        }
    }

    override suspend fun setBootstrapDnsMode(mode: DnsBoostrapMode) {
        when(mode) {
            is DnsBoostrapMode.Custom -> {
                dnsConfigJob?.cancel()
                dnsConfigJob = null
                DnsConfigManager.update(mode.config.protocol, mode.config.upstream ?: DnsBoostrapConfig.DEFAULT_UPSTREAM)
            }
            DnsBoostrapMode.System -> {
                if(dnsConfigJob?.isActive == true) return
                dnsConfigJob = backendScope.launch {
                    networkMonitor.connectivityStateFlow.distinctUntilChangedBy { it.underlyingDnsInfo }
                        .collect { state ->
                            val dnsSettings = state.underlyingDnsInfo

                            Timber.d("Detected automatic DNS Settings: $dnsSettings")

                            val bootstrapConfig = when (dnsSettings.privateDnsMode) {
                                PrivateDnsMode.OFF -> {
                                    DnsBoostrapConfig.Plain(
                                        dnsSettings.servers.firstOrNull() ?: DnsBoostrapConfig.DEFAULT_UPSTREAM
                                    )
                                }

                                PrivateDnsMode.AUTOMATIC -> {
                                    if (!dnsSettings.privateDnsHostname.isNullOrBlank()) {
                                        // Network provided hostname via DHCP
                                        DnsBoostrapConfig.DoT(dnsSettings.privateDnsHostname)
                                    } else {
                                        // No hostname, Android falls back to plain DNS
                                        DnsBoostrapConfig.Plain(
                                            dnsSettings.servers.firstOrNull() ?: DnsBoostrapConfig.DEFAULT_UPSTREAM
                                        )
                                    }
                                }

                                PrivateDnsMode.HOSTNAME -> {
                                    val hostname = dnsSettings.privateDnsHostname
                                    if (!hostname.isNullOrBlank()) {
                                        DnsBoostrapConfig.DoT(hostname)
                                    } else {
                                        // fallback
                                        DnsBoostrapConfig.Plain(
                                            dnsSettings.servers.firstOrNull() ?: DnsBoostrapConfig.DEFAULT_UPSTREAM
                                        )
                                    }
                                }
                            }

                            Timber.d("Configuring automatic DNS Settings: $bootstrapConfig")

                            DnsConfigManager.update(
                                bootstrapConfig.protocol,
                                bootstrapConfig.upstream ?: DnsBoostrapConfig.DEFAULT_UPSTREAM
                            )
                        }
                }
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun start(tunnel: Tunnel, mode: BackendMode): Result<Unit> = runCatching {

        Timber.i("Start request for tunnel: ${tunnel.id}")

        tunnel.updateState(Tunnel.State.Starting)

        val runConfig = mode.config.copy(
            peers = mode.config.peers.map { peerSection ->
                if (!peerSection.isStaticallyConfigured) {
                    peerSection.endpoint?.split(":")?.getOrNull(1)?.let { port ->
                        peerSection.copy(endpoint = "$DUMMY_ADDRESS:$port")
                    } ?: peerSection
                } else peerSection
            }
        )

        val isFirstTunnel = runtimeStates.isEmpty()

        try {

            if (isFirstTunnel) {
                Timber.i("Registering global status callback on first tunnel")
                VpnBackend.setStatusCallback(statusCallback)
                statusCallbackRegistered.store(true)
            }

            val (handle, ifName) = tunnelMutex.withLock {

                val ifName = WGT_INTERFACE_PREFIX + tunnel.id

                if (runtimeStates.containsKey(tunnel.id)) {
                    throw BackendException.StateConflict("Tunnel ${tunnel.id} is already in use")
                }

                val handle = when (mode) {
                    is BackendMode.Proxy.KillSwitchPrimary,
                    is BackendMode.Proxy.Standard -> {
                        val bridgeNeeded = mode is BackendMode.Proxy.KillSwitchPrimary
                        startProxyTunnel(ifName, runConfig, bridgeNeeded)
                    }

                    is BackendMode.Vpn -> startVpnTunnel(tunnel, ifName, runConfig)
                }

                if (handle < 0) {
                    throw BackendException.InternalError("Tunnel failed with internal error code $handle")
                }

                handle to ifName
            }

            val running = RunningTunnel(
                handle = handle,
                interfaceName = ifName,
                mode = mode,
                tunnel = tunnel,
                currentPreferIpv6 = tunnel.ipStrategy is Tunnel.IpStrategy.PreferIpv6,
            )

            val runtime = TunnelRuntimeState(
                running = running,
                active = ActiveTunnel(
                    state = Tunnel.State.Starting,
                    interfaceName = ifName,
                    mode = mode,
                    uptime = System.currentTimeMillis()
                )
            )

            // SINGLE SOURCE OF TRUTH INSERT
            upsertTunnel(tunnel.id) { runtime }

            handleIndex[handle] = tunnel.id

            val job = startTunnelJobs(tunnel, running)

            // update runtime with job (safe mutation)
            modifyTunnel(tunnel.id) { current ->
                current.copy(
                    running = current.running.copy(job = job)
                )
            }

            addActiveTunnel(
                tunnel.id,
                ActiveTunnel(
                    state = Tunnel.State.Starting,
                    mode = mode,
                    uptime = System.currentTimeMillis()
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Tunnel, cleaning up tunnel")
            handleIndex.remove(tunnel.id)
            tunnelLocks[tunnel.id]?.withLock {
                cleanupTunnelLocked(tunnel.id)
            }
            throw e
        }
    }

    private fun updatePeersFromCache(
        runningTunnel: RunningTunnel,
        preferIpv6: Boolean
    ): List<PeerSection> {

        return runningTunnel.mode.config.peers.map { peer ->
            val endpoint = peer.endpoint ?: return@map peer
            val port = endpoint.substringAfterLast(":")

            val dnsCache = runningTunnel.peerBootstrapCache[peer.publicKey]
                ?: return@map peer

            val ip = when {
                preferIpv6 -> dnsCache.ipv6.firstOrNull() ?: dnsCache.ipv4.firstOrNull()
                else -> dnsCache.ipv4.firstOrNull() ?: dnsCache.ipv6.firstOrNull()
            } ?: return@map peer

            peer.copy(endpoint = "$ip:$port")
        }
    }

    private fun startTunnelJobs(
        tunnel: Tunnel,
        runningSnapshot: RunningTunnel
    ) = backendScope.launch(Dispatchers.IO) {

        val tunnelId = tunnel.id

        /**
         * ----------------------------
         * 1. Peer bootstrapping job
         * ----------------------------
         */
        val needsBootstrapping = runningSnapshot.mode.config.peers.any {
            !it.isStaticallyConfigured
        }

        if (needsBootstrapping) {
            launch {
                val resolved = resolvePeers(runningSnapshot)

                // 1. Update state ONLY
                modifyTunnel(tunnelId) { runtime ->
                    runtime.copy(
                        running = runtime.running.copy(
                            peerBootstrapCache = resolved
                        )
                    )
                }

                // 2. Apply side-effect AFTER state is updated
                updateTunnelPeers(tunnelId) { running ->
                    updatePeersFromCache(running, preferIpv6 = true)
                }
            }
        }

        /**
         * ----------------------------
         * 2. IPv6 strategy jobs
         * ----------------------------
         */
        when (val strat = runningSnapshot.tunnel.ipStrategy) {

            is Tunnel.IpStrategy.PreferIpv6 -> {

                /**
                 * IPv4 fallback job
                 */
                if (strat.fallbackToIpv4Enabled) {
                    launch {

                        var failureCount = 0
                        var firstFailureTime = 0L

                        status
                            .map { it.activeTunnels[tunnelId] }
                            .distinctUntilChangedBy { it?.state to it?.lastStateChangeMs }
                            .collect { active ->

                                val activeTunnel = active ?: return@collect
                                val now = System.currentTimeMillis()

                                val usingIpv6 = activeTunnel.activeConfig?.peers?.any {
                                    it.endpoint?.startsWith("[") == true
                                } ?: false

                                when (activeTunnel.state) {

                                    is Tunnel.State.Up.Healthy -> {
                                        failureCount = 0
                                        firstFailureTime = 0L
                                    }

                                    is Tunnel.State.Up.HandshakeFailure -> {

                                        if (failureCount == 0) firstFailureTime = now
                                        failureCount++

                                        val duration = now - firstFailureTime

                                        if (failureCount < IPV4_FALLBACK_FAILURE_COUNT ||
                                            duration < IPV4_FALLBACK_FAILURE_DURATION ||
                                            !usingIpv6
                                        ) return@collect

                                        // 👇 capture decision inside mutex
                                        val shouldSwitch = withTunnelState(tunnelId) { runtime ->
                                            runtime?.running?.currentPreferIpv6 == true
                                        } ?: false

                                        if (!shouldSwitch) return@collect

                                        // 1. update state
                                        modifyTunnel(tunnelId) { runtime ->
                                            runtime.copy(
                                                running = runtime.running.copy(
                                                    currentPreferIpv6 = false
                                                )
                                            )
                                        }

                                        // 2. apply side-effect
                                        updateTunnelPeers(tunnelId) { running ->
                                            updatePeersFromCache(running, preferIpv6 = false)
                                        }

                                        failureCount = 0
                                        firstFailureTime = 0L
                                    }

                                    else -> Unit
                                }
                            }
                    }
                }

                /**
                 * IPv6 recovery job
                 */
                if (strat.recoveryEnabled) {
                    launch {

                        var lastRecoveredNetworkKey: String? = null

                        combine(
                            status
                                .map { it.activeTunnels[tunnelId] }
                                .distinctUntilChangedBy { it?.state to it?.lastStateChangeMs },

                            networkMonitor.connectivityStateFlow
                                .distinctUntilChangedBy { it.activeNetwork }
                        ) { active, net ->
                            active to net
                        }.collect { (active, connectivity) ->

                            val activeTunnel = active ?: return@collect
                            val now = System.currentTimeMillis()

                            val stable =
                                now - activeTunnel.lastStateChangeMs >= RECOVERY_STABILITY_WINDOW

                            val networkKey = connectivity.activeNetwork.key()

                            if (activeTunnel.state !is Tunnel.State.Up.Healthy) return@collect
                            if (!stable) return@collect
                            if (!connectivity.hasIpv6) return@collect
                            if (lastRecoveredNetworkKey == networkKey) return@collect

                            // 👇 capture atomic decision + mutation
                            var switched = false

                            modifyTunnel(tunnelId) { runtime ->

                                if (runtime.running.currentPreferIpv6) return@modifyTunnel runtime

                                val cache = runtime.running.peerBootstrapCache
                                if (cache.isEmpty()) return@modifyTunnel runtime
                                if (cache.values.none { it.ipv6.isNotEmpty() }) return@modifyTunnel runtime

                                Timber.i("IPv6 recovery triggered for ${tunnel.name}")

                                switched = true

                                runtime.copy(
                                    running = runtime.running.copy(
                                        currentPreferIpv6 = true
                                    )
                                )
                            }

                            // 👇 apply side-effect ONLY if we switched
                            if (switched) {
                                updateTunnelPeers(tunnelId) { running ->
                                    updatePeersFromCache(running, preferIpv6 = true)
                                }

                                // update AFTER successful switch
                                lastRecoveredNetworkKey = networkKey
                            }
                        }
                    }
                }
            }

            is Tunnel.IpStrategy.Ipv4Only -> Unit
        }

        /**
         * ----------------------------
         * 3. Feature jobs
         * ----------------------------
         */
        runningSnapshot.tunnel.features.forEach { feature ->

            when (feature) {

                is Tunnel.Feature.ActiveConfigMonitor -> {
                    launch {
                        ActiveConfigFeature().monitor(
                            tunnelId = tunnelId,
                            getRawActiveConfig = { id ->
                                withTunnelState(id) { it?.running?.let(::getActiveConfigInner) }
                            },
                            statusUpdater = { id, activeConfig ->
                                _status.update { backend ->
                                    val current = backend.activeTunnels[id] ?: return@update backend
                                    backend.copy(
                                        activeTunnels = backend.activeTunnels +
                                                (id to current.copy(activeConfig = activeConfig))
                                    )
                                }
                            }
                        )
                    }
                }

                Tunnel.Feature.DynamicDNS -> {
                    launch {

                        var failureCount = 0
                        var firstFailureTime = 0L

                        status
                            .map { it.activeTunnels[tunnelId] }
                            .distinctUntilChangedBy { it?.state }
                            .collect { active ->

                                val activeTunnel = active ?: return@collect
                                val now = System.currentTimeMillis()

                                when (activeTunnel.state) {

                                    is Tunnel.State.Up.Healthy -> {
                                        failureCount = 0
                                        firstFailureTime = 0L
                                    }

                                    is Tunnel.State.Up.HandshakeFailure -> {

                                        if (failureCount == 0) firstFailureTime = now
                                        failureCount++

                                        val duration = now - firstFailureTime

                                        if (failureCount < DYNAMIC_DNS_FAILURE_COUNT ||
                                            duration < DYNAMIC_DNS_FAILURE_DURATION
                                        ) return@collect

                                        val resolved = withTunnelState(tunnelId) { runtime ->
                                            runtime?.running
                                        }?.let { resolvePeers(it) } ?: return@collect

                                        modifyTunnel(tunnelId) { runtime ->

                                            val latest = runtime.running

                                            val updatedPeers = latest.mode.config.peers.map { peer ->
                                                val endpoint = peer.endpoint ?: return@map peer
                                                val port = endpoint.substringAfterLast(":")

                                                val resolvedPeer = resolved[peer.publicKey]
                                                    ?: return@map peer

                                                val newIp = resolvedPeer.ipv4.firstOrNull()
                                                    ?: resolvedPeer.ipv6.firstOrNull()
                                                    ?: return@map peer

                                                peer.copy(endpoint = "$newIp:$port")
                                            }

                                            val updatedRunning = latest.copy(
                                                peerBootstrapCache = resolved,
                                                mode = latest.mode.withConfig(
                                                    config = latest.mode.config.copy(
                                                        peers = updatedPeers
                                                    )
                                                )
                                            )

                                            runtime.copy(running = updatedRunning)
                                        }

                                        // IMPORTANT: call AFTER modifyTunnel
                                        updateTunnelPeers(tunnelId) { running ->
                                            updatePeersFromCache(running, preferIpv6 = true)
                                        }

                                        failureCount = 0
                                        firstFailureTime = 0L
                                    }

                                    else -> Unit
                                }
                            }
                    }
                }
            }
        }
    }


    private suspend fun performStopLocked(id: Int) {
        val runtime = runtimeStates[id]

        if (runtime != null) {
            try {
                when (val mode = runtime.running.mode) {
                    is BackendMode.Proxy -> {
                        val withBridge = mode is BackendMode.Proxy.KillSwitchPrimary
                        stopProxyTunnel(runtime.running, withBridge)
                    }
                    is BackendMode.Vpn -> stopVpnTunnel(runtime.running)
                }
            } catch (e: Exception) {
                Timber.e(e, "stop failed")
            }
        }

        cleanupTunnelLocked(id)
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun cleanupTunnelLocked(tunnelId: Int) {
        val runtime = runtimeStates.remove(tunnelId) ?: return

        handleIndex.remove(runtime.running.handle)

        _status.update {
            it.copy(activeTunnels = it.activeTunnels - tunnelId)
        }

        runtime.running.tunnel.updateState(Tunnel.State.Down)

        runtime.running.job?.cancel()

        if (statusCallbackRegistered.load() && runtimeStates.isEmpty()) {
            VpnBackend.setStatusCallback(null)
        }
    }

    private fun addActiveTunnel(tunnelId: Int, activeTunnel: ActiveTunnel) {
        _status.update {
            it.copy(
                activeTunnels = it.activeTunnels + (tunnelId to activeTunnel)
            )
        }
    }

    private fun updateActiveTunnel(tunnelId: Int, activeTunnel: ActiveTunnel) {
        _status.update { current ->
            current.copy(
                activeTunnels = current.activeTunnels + (tunnelId to activeTunnel)
            )
        }
    }

    private fun startProxyTunnel(ifName: String, config: Config, withHevSocks5Bridge: Boolean) : Int {
        return withTunnelScripts(config, ScriptDirection.UP) {
            val bypass = if(withHevSocks5Bridge) 1 else 0
            val handle = ProxyBackend.awgStartProxy(ifName, config.asQuickString(), uapiPath, bypass)
            if(withHevSocks5Bridge) vpnService.get().startHevSocks5Bridge()
            if (handle < 0) {
                throw BackendException.InternalError("Internal native error")
            }
            handle
        }
    }


    private fun startShellSession() : Boolean {
        return try {
            rootShell.start()
            true
        } catch (e : RootShellException) {
            Timber.e(e, "Failed to start session for config scripts. Skipping scripts")
            false
        }
    }

    private suspend fun resolvePeers(
        runningTunnel: RunningTunnel
    ): Map<PublicKey, DnsBootstrapResult> {

        val peersToResolve = runningTunnel.mode.config.peers
            .filter { !it.isStaticallyConfigured }

        if (peersToResolve.isEmpty()) return emptyMap()

        val bypassNeeded =
            runningTunnel.mode is BackendMode.Vpn ||
                    _status.value.killSwitch.enabled

        val results = mutableMapOf<PublicKey, DnsBootstrapResult>()

        exponentialBackoffForever {

            Timber.d("Peer resolution attempt (resolved=${results.size}/${peersToResolve.size})")

            for (peer in peersToResolve) {

                // already resolved → skip
                if (results.containsKey(peer.publicKey)) continue

                val endpoint = peer.endpoint ?: continue
                val host = endpoint.substringBeforeLast(":")

                val dnsResult = try {
                    DnsConfigManager.resolveHostBootstrap(
                        host = host,
                        bypass = bypassNeeded
                    )
                } catch (e: Exception) {
                    Timber.w(e, "DNS failed for $host")
                    continue
                }

                if (dnsResult.ipv4.isEmpty() && dnsResult.ipv6.isEmpty()) {
                    Timber.w("No IPs for $host")
                    continue
                }

                results[peer.publicKey] = dnsResult.copy(
                    ipv6 = dnsResult.ipv6.map { "[$it]" }
                )

                Timber.d("Resolved $host → ${results[peer.publicKey]}")
            }

            // ✅ exit condition (IMPORTANT)
            if (results.size == peersToResolve.size) {
                return@exponentialBackoffForever
            }

            // ❌ force retry
            throw IllegalStateException("Incomplete resolution, retrying...")
        }

        return results
    }

    private suspend fun updateTunnelPeers(
        tunnelId: Int,
        transform: (RunningTunnel) -> List<PeerSection>
    ) {
        val mutex = tunnelLocks.getOrPut(tunnelId) { Mutex() }

        mutex.withLock {
            val runtime = runtimeStates[tunnelId] ?: return
            val running = runtime.running

            val updatedPeers = transform(running)

            val updatedConfig = running.mode.config.copy(
                peers = updatedPeers
            )

            val newRunning = running.copy(
                mode = running.mode.withConfig(config = updatedConfig)
            )

            val newRuntime = runtime.copy(running = newRunning)

            try {
                when (running.mode) {
                    is BackendMode.Proxy.KillSwitchPrimary,
                    is BackendMode.Proxy.Standard -> {
                        ProxyBackend.awgUpdateProxyTunnelPeers(
                            running.handle,
                            updatedConfig.asQuickString()
                        )
                    }

                    is BackendMode.Vpn -> {
                        VpnBackend.awgUpdateTunnelPeers(
                            running.handle,
                            updatedConfig.asQuickString()
                        )
                    }
                }

                // ✅ commit ONLY after success
                runtimeStates[tunnelId] = newRuntime

            } catch (e: Exception) {
                Timber.e(e, "Failed to update peers for tunnel $tunnelId")
            }
        }
    }


    private fun startVpnTunnel(tunnel: Tunnel, ifName: String, config: Config): Int {
        val service = getVpnService() ?: throw BackendException.InternalError("VPN service not available")

        return withTunnelScripts(config, ScriptDirection.UP) {
            val fd = service.createTunInterface(tunnel, config)?.detachFd()
                ?: throw BackendException.Unauthorized("Failed to create tun interface")

            val handle = VpnBackend.awgTurnOn(ifName, fd, config.asQuickString(), uapiPath)

            if (handle < 0) {
                throw BackendException.InternalError("Internal native error")
            }

            service.protect(VpnBackend.awgGetSocketV4(handle))
            service.protect(VpnBackend.awgGetSocketV6(handle))

            handle
        }
    }

    override suspend fun setKillSwitch(config: KillSwitchConfig) = runCatching {
        if (vpnService.isDone){
            vpnService.get().setKillSwitch(null)
            vpnService.get().setKillSwitch(config)
        } else {
            val service = getVpnService() ?: throw BackendException.InternalError("VPN service not available")
            service.setKillSwitch(config)
        }

        _status.update { current ->
            current.copy(
                killSwitch = KillSwitchState(
                    enabled = true,
                    config = config,
                    primaryTunnel = current.killSwitch.primaryTunnel
                )
            )
        }
    }

    override suspend fun stop(id: Int): Result<Unit> {
        val mutex = tunnelLocks.getOrPut(id) { Mutex() }

        return mutex.withLock {
            performStopLocked(id)
            Result.success(Unit)
        }
    }

    private fun stopProxyTunnel(runningTunnel: RunningTunnel, withHevSocks5Bridge: Boolean) {
        withTunnelScripts(runningTunnel.mode.config, ScriptDirection.DOWN) {
            if(withHevSocks5Bridge) vpnService.get().stopHevSocks5Bridge()
            ProxyBackend.awgTurnProxyTunnelOff(runningTunnel.handle)
        }
    }

    private fun stopVpnTunnel(runningTunnel: RunningTunnel) {
        withTunnelScripts(runningTunnel.mode.config, ScriptDirection.DOWN) {
            VpnBackend.awgTurnOff(runningTunnel.handle)
            context.stopService(Intent(context, VpnService::class.java))
        }
    }

    private fun getVpnService() : VpnService? {
        if(!vpnService.isDone) {
            if (android.net.VpnService.prepare(context) != null) {
                throw BackendException.Unauthorized("Permission unavailable to use VpnService")
            }
            context.startService(Intent(context, VpnService::class.java))
        }
        val service = try {
            vpnService.get(2, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            Timber.e(e,"Timed out getting VpnService..")
            null
        }
        return service
    }

    private inline fun <T> withTunnelScripts(
        config: Config,
        direction: ScriptDirection,
        block: () -> T
    ): T {
        val iface = config.`interface`
        val needsShell = iface.hasScripts
        val shellActive = needsShell && startShellSession()

        try {
            if (shellActive) {
                val preCmds = when (direction) {
                    ScriptDirection.UP -> iface.preUp
                    ScriptDirection.DOWN -> iface.preDown
                }
                preCmds?.let { rootShell.run(*it.toTypedArray()) }
            }

            val result = block()

            if (shellActive) {
                val postCmds = when (direction) {
                    ScriptDirection.UP -> iface.postUp
                    ScriptDirection.DOWN -> iface.postDown
                }
                postCmds?.let { rootShell.run(*it.toTypedArray()) }
            }

            return result
        } finally {
            if (shellActive) {
                rootShell.stop()
            }
        }
    }

    private suspend fun modifyTunnel(
        tunnelId: Int,
        block: (TunnelRuntimeState) -> TunnelRuntimeState?
    ) {
        val mutex = tunnelLocks.getOrPut(tunnelId) { Mutex() }

        mutex.withLock {
            val current = runtimeStates[tunnelId] ?: return
            val updated = block(current) ?: return
            runtimeStates[tunnelId] = updated
        }
    }

    private suspend inline fun <T> withTunnelState(
        tunnelId: Int,
        block: (TunnelRuntimeState?) -> T
    ): T {
        val mutex = tunnelLocks.getOrPut(tunnelId) { Mutex() }

        return mutex.withLock {
            block(runtimeStates[tunnelId])
        }
    }

    private suspend fun upsertTunnel(
        tunnelId: Int,
        block: (TunnelRuntimeState?) -> TunnelRuntimeState
    ) {
        val mutex = tunnelLocks.getOrPut(tunnelId) { Mutex() }

        mutex.withLock {
            val current = runtimeStates[tunnelId]
            val updated = block(current)
            runtimeStates[tunnelId] = updated
        }
    }

    private suspend fun getRuntimeState(tunnelId: Int): TunnelRuntimeState? {
        val mutex = tunnelLocks.getOrPut(tunnelId) { Mutex() }
        return mutex.withLock { runtimeStates[tunnelId] }
    }

    private fun removeTunnel(tunnelId: Int) {
        val state = runtimeStates.remove(tunnelId) ?: return
        handleIndex.remove(state.running.handle)
    }

    companion object {
        const val IPV4_FALLBACK_FAILURE_COUNT = 4
        const val IPV4_FALLBACK_FAILURE_DURATION = 10_000L
        const val DYNAMIC_DNS_FAILURE_COUNT = 8
        const val DYNAMIC_DNS_FAILURE_DURATION = 60_000L
        const val WGT_INTERFACE_PREFIX = "wgtun"
        const val DUMMY_ADDRESS = "192.0.2.1"
        const val DEFAULT_MTU = 1280
        const val RECOVERY_STABILITY_WINDOW = 5_000L
        // for consumer to set AOVPN callback
        var alwaysOnCallback: VpnService.AlwaysOnCallback? = null
        @Volatile
        var vpnService = CompletableFuture<VpnService>()
    }
}