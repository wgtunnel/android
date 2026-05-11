package com.zaneschepke.tunnel.backend

import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.networkmonitor.PrivateDnsMode
import com.zaneschepke.networkmonitor.StableNetworkEngine
import com.zaneschepke.tunnel.DnsConfigManager
import com.zaneschepke.tunnel.NotificationProvider
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.event.TunnelEvent
import com.zaneschepke.tunnel.model.*
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.tunnel.state.KillSwitchState
import com.zaneschepke.tunnel.util.BackendException
import com.zaneschepke.tunnel.util.buildResolvedPeers
import com.zaneschepke.tunnel.util.exponentialBackoffForever
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class TunnelBackend(
    private val scope: CoroutineScope,
    private val stableNetworkEngine: StableNetworkEngine,
    private val networkMonitor: NetworkMonitor,
    override val notificationProvider: NotificationProvider,
) : Backend {

    private val serviceHolder: ServiceHolder by inject(ServiceHolder::class.java)
    private val actor: TunnelActor by inject(TunnelActor::class.java)

    private val _status = MutableStateFlow(BackendStatus())
    override val status: Flow<BackendStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<TunnelEvent>(extraBufferCapacity = 32)
    override val events = _events.asSharedFlow()

    private var dnsConfigJob: Job? = null

    init {
        scope.launch {
            var hadVpnTunnels = false
            var hadProxyTunnels = false

            actor.state.collect { actorState ->
                val runtimes = actorState.byTunnelId.values

                val hasVpnTunnels = runtimes.any { it.running.mode is BackendMode.Vpn }

                val hasProxyTunnels = runtimes.any { it.running.mode is BackendMode.Proxy }

                val activeTunnels = actorState.byTunnelId.mapValues { it.value.active }

                _status.update { current -> current.copy(activeTunnels = activeTunnels) }

                // Start jobs if missing
                actorState.byTunnelId.forEach { (id, runtime) ->
                    if (runtime.running.job != null) return@forEach

                    val job = launchTunnelJobs(id, runtime.running)

                    actor.send(TunnelCommand.AttachJob(id, job))
                }

                if (hadVpnTunnels && !shouldKeepVpnServiceAlive(hasVpnTunnels)) {
                    serviceHolder.stopVpnService()
                }

                if (hadProxyTunnels && !hasProxyTunnels) {
                    serviceHolder.stopTunnelService()
                }

                hadVpnTunnels = hasVpnTunnels
                hadProxyTunnels = hasProxyTunnels
            }
        }
    }

    private fun shouldKeepVpnServiceAlive(hasVpnTunnels: Boolean): Boolean {
        return hasVpnTunnels || _status.value.killSwitch.enabled
    }

    override suspend fun start(tunnel: Tunnel, mode: BackendMode): Result<Unit> = runCatching {
        val existing = actor.state.value.byTunnelId[tunnel.id]

        if (existing != null) {
            Timber.d("Tunnel ${tunnel.id} already running — ignoring start")
            return@runCatching
        }

        actor.send(TunnelCommand.Start(tunnel, mode))
    }

    override fun setAlwaysOnCallback(alwaysOnCallback: VpnService.AlwaysOnCallback) {
        ServiceHolder.alwaysOnCallback = alwaysOnCallback
    }

    override suspend fun stop(id: Int): Result<Unit> = runCatching {
        actor.state.value.byTunnelId[id]
            ?: throw BackendException.StateConflict("Tunnel $id is not active or no longer exists")

        actor.send(TunnelCommand.Stop(id))
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
                        config =
                            KillSwitchConfig(
                                allowedIps = emptySet(),
                                metered = false,
                                dualStack = false,
                            ),
                        primaryTunnel = current.killSwitch.primaryTunnel,
                    )
            )
        }
    }

    override suspend fun setBootstrapDnsMode(mode: DnsBoostrapMode) {
        _status.update { it.copy(dnsMode = mode) }

        when (mode) {
            is DnsBoostrapMode.Custom -> {
                Timber.d("DNS Boostrap mode set to custom, disabling system dns monitoring")
                dnsConfigJob?.cancel()
                dnsConfigJob = null

                DnsConfigManager.update(
                    mode.config.protocol,
                    mode.config.upstream ?: DnsBoostrapConfig.DEFAULT_UPSTREAM,
                )
            }

            DnsBoostrapMode.System -> {
                startSystemDnsMonitoring()
            }
        }
    }

    override suspend fun stopAllOfType(modeClass: KClass<out BackendMode>): Result<Unit> =
        runCatching {
            val idsToStop =
                _status.value.activeTunnels
                    .filter { (_, activeTunnel) -> modeClass.isInstance(activeTunnel.mode) }
                    .keys

            idsToStop.forEach { id -> stop(id) }
        }

    override suspend fun stopAllActiveTunnels(): Result<Unit> = runCatching {
        _status.value.activeTunnels.forEach { (id, _) -> stop(id) }
    }

    private fun startSystemDnsMonitoring() {
        if (dnsConfigJob?.isActive == true) return

        dnsConfigJob = scope.launch {
            networkMonitor.connectivityStateFlow
                .distinctUntilChangedBy { it.underlyingDnsInfo }
                .collect { state ->
                    val dns = state.underlyingDnsInfo

                    val config =
                        when (dns.privateDnsMode) {
                            PrivateDnsMode.OFF -> {
                                DnsBoostrapConfig.Plain(
                                    dns.servers.firstOrNull() ?: DnsBoostrapConfig.DEFAULT_UPSTREAM
                                )
                            }

                            PrivateDnsMode.AUTOMATIC -> {
                                dns.privateDnsHostname
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { DnsBoostrapConfig.DoT(it) }
                                    ?: DnsBoostrapConfig.Plain(
                                        dns.servers.firstOrNull()
                                            ?: DnsBoostrapConfig.DEFAULT_UPSTREAM
                                    )
                            }

                            PrivateDnsMode.HOSTNAME -> {
                                dns.privateDnsHostname
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { DnsBoostrapConfig.DoT(it) }
                                    ?: DnsBoostrapConfig.Plain(
                                        dns.servers.firstOrNull()
                                            ?: DnsBoostrapConfig.DEFAULT_UPSTREAM
                                    )
                            }
                        }

                    DnsConfigManager.update(
                        config.protocol,
                        config.upstream ?: DnsBoostrapConfig.DEFAULT_UPSTREAM,
                    )
                }
        }
    }

    private fun launchTunnelJobs(tunnelId: Int, running: RunningTunnel): Job = scope.launch {
        val staticConfig = running.mode.config.peers.all { it.isStaticallyConfigured }

        if (!staticConfig) {
            launch {
                actor.send(TunnelCommand.BeginDnsResolution(tunnelId))

                val cache = resolvePeers(running)

                val updatedRunning = running.copy(peerBootstrapCache = cache)

                val networkHasIpv6 = stableNetworkEngine.stableState.value?.state?.hasIpv6 ?: false

                val peers =
                    updatedRunning.buildResolvedPeers(
                        preferIpv6 = running.currentPreferIpv6 && networkHasIpv6
                    )

                actor.send(
                    TunnelCommand.ApplyResolvedPeers(
                        tunnelId = tunnelId,
                        cache = cache,
                        peers = peers,
                    )
                )
                actor.send(TunnelCommand.EndDnsResolution(tunnelId))
            }
        }
        if (!staticConfig) {
            when (val strategy = running.tunnel.ipStrategy) {
                Tunnel.IpStrategy.Ipv4Only -> Unit
                is Tunnel.IpStrategy.PreferIpv6 -> {
                    if (strategy.recoveryEnabled || strategy.fallbackToIpv4Enabled) {
                        launch {
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
                                    val newKey = stable.key

                                    if (newKey != currentNetworkKey) {
                                        currentNetworkKey = newKey
                                        hasRecoveredOnThisNetwork = false
                                        hasFallenBackOnThisNetwork = false
                                        healthySinceMs = null
                                        failureCount = 0
                                        firstFailureTime = 0L
                                        ipv6Bad = false

                                        Timber.d(
                                            "Stable network changed resetting IPv6 state ($newKey)"
                                        )
                                    }

                                    val now = System.currentTimeMillis()

                                    val isUsingIpv6 =
                                        activeTunnel.activeConfig?.peers?.any {
                                            it.endpoint?.startsWith("[") == true
                                        } ?: false

                                    val isHealthy = activeTunnel.state is Tunnel.State.Up.Healthy
                                    val isHandshakeFailure =
                                        activeTunnel.state is Tunnel.State.Up.HandshakeFailure

                                    healthySinceMs = if (isHealthy) healthySinceMs ?: now else null
                                    val healthyDuration = healthySinceMs?.let { now - it } ?: 0L

                                    Timber.d(
                                        "IPv6 strategy | net=$newKey | usingIPv6=$isUsingIpv6 | healthy=$isHealthy | healthyDuration=${healthyDuration}ms | hasRecovered=$hasRecoveredOnThisNetwork | hasFallback=$hasFallenBackOnThisNetwork | hasIPv6=${stable.state.hasIpv6} | ipv6Bad=$ipv6Bad | state=${activeTunnel.state}"
                                    )

                                    if (isHealthy) {
                                        failureCount = 0
                                        firstFailureTime = 0L
                                    }

                                    // Fallback IPv6 to IPv4
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

                                            Timber.d(
                                                "Fallback to IPv4 triggered on $newKey (marking IPv6 bad)"
                                            )

                                            _events.emit(TunnelEvent.FallbackToIpv4(tunnelId))

                                            actor.send(
                                                TunnelCommand.UpdatePeers(
                                                    tunnelId,
                                                    preferIpv6 = false,
                                                )
                                            )
                                        }
                                    }

                                    // Recovery IPv4 to IPv6
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

                                            Timber.d(
                                                "Recovered to IPv6 on $newKey (healthy for ${healthyDuration}ms)"
                                            )

                                            _events.emit(TunnelEvent.RecoveredToIpv6(tunnelId))

                                            actor.send(
                                                TunnelCommand.UpdatePeers(
                                                    tunnelId,
                                                    preferIpv6 = true,
                                                )
                                            )
                                        }
                                    }
                                }
                                .collect {}
                        }
                    }
                }
            }
        }

        running.tunnel.features.forEach { feature ->
            when (feature) {
                is Tunnel.Feature.ActiveConfigMonitor -> {
                    launch {
                        while (isActive) {
                            actor.send(TunnelCommand.UpdateActiveConfig(tunnelId = tunnelId))
                            delay(1.seconds)
                        }
                    }
                }
                Tunnel.Feature.DynamicDNS -> {
                    if (!staticConfig) {
                        val controller =
                            DynamicDnsController(
                                stabilityWindowMs = DDNS_STABILITY_WINDOW,
                                failureWindowMs = DDNS_FAILURE_WINDOW,
                                minResolveIntervalMs = DDNS_MIN_RESOLVE_INTERVAL,
                            )

                        launch {
                            combine(
                                    stableNetworkEngine.stableState.filterNotNull(),
                                    status.mapNotNull { it.activeTunnels[tunnelId] },
                                ) { stable, activeTunnel ->
                                    stable to activeTunnel
                                }
                                .collect { (stable, activeTunnel) ->
                                    if (!stable.state.hasInternet()) return@collect

                                    val now = System.currentTimeMillis()

                                    val shouldResolve =
                                        controller.shouldResolve(
                                            now = now,
                                            isHealthy =
                                                activeTunnel.state is Tunnel.State.Up.Healthy,
                                            isHandshakeFailure =
                                                activeTunnel.state
                                                    is Tunnel.State.Up.HandshakeFailure,
                                        )

                                    if (!shouldResolve) return@collect

                                    val resolved = resolvePeers(running)

                                    val changed = controller.diff(resolved)
                                    if (changed.isEmpty()) return@collect

                                    controller.markResolved(now)

                                    _events.emit(
                                        TunnelEvent.DynamicDnsUpdate(
                                            tunnelId = tunnelId,
                                            changedPeers = changed,
                                        )
                                    )

                                    actor.send(
                                        TunnelCommand.ApplyResolvedPeers(
                                            tunnelId = tunnelId,
                                            cache = resolved,
                                            peers =
                                                running
                                                    .copy(peerBootstrapCache = resolved)
                                                    .buildResolvedPeers(
                                                        preferIpv6 = running.currentPreferIpv6
                                                    ),
                                        )
                                    )
                                }
                        }
                    }
                }
            }
        }
    }

    private suspend fun resolvePeers(
        runningTunnel: RunningTunnel
    ): Map<PublicKey, DnsBootstrapResult> {

        val peersToResolve = runningTunnel.mode.config.peers.filter { !it.isStaticallyConfigured }

        if (peersToResolve.isEmpty()) return emptyMap()

        val bypassNeeded = runningTunnel.mode is BackendMode.Vpn || _status.value.killSwitch.enabled

        val results = mutableMapOf<PublicKey, DnsBootstrapResult>()

        exponentialBackoffForever {
            Timber.d("Peer resolution attempt (resolved=${results.size}/${peersToResolve.size})")

            for (peer in peersToResolve) {

                // already resolved
                if (results.containsKey(peer.publicKey)) continue

                val endpoint = peer.endpoint ?: continue
                val host = endpoint.substringBeforeLast(":")

                val dnsResult =
                    try {
                        DnsConfigManager.resolveHostBootstrap(host = host, bypass = bypassNeeded)
                    } catch (e: Exception) {
                        Timber.w(e, "DNS failed for $host")
                        continue
                    }

                if (dnsResult.ipv4.isEmpty() && dnsResult.ipv6.isEmpty()) {
                    Timber.w("No IPs for $host")
                    continue
                }

                results[peer.publicKey] =
                    dnsResult.copy(
                        ipv4 = dnsResult.ipv4,
                        // normalize
                        ipv6 = dnsResult.ipv6.map { "[$it]" },
                    )

                Timber.d("Resolved $host to ${results[peer.publicKey]}")
            }

            // exit
            if (results.size == peersToResolve.size) {
                return@exponentialBackoffForever
            }

            // force retry
            throw IllegalStateException("Incomplete resolution, retrying...")
        }

        return results
    }

    companion object {
        private const val DDNS_MIN_RESOLVE_INTERVAL = 30_000L
        private const val DDNS_FAILURE_WINDOW = 10_000L
        private const val DDNS_STABILITY_WINDOW = 15_000L
        private const val IPV4_FALLBACK_FAILURE_COUNT = 4
        private const val IPV4_FALLBACK_FAILURE_DURATION = 10_000L
        private const val RECOVERY_STABILITY_WINDOW = 5_000L
    }
}
