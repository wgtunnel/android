package com.zaneschepke.wireguardautotunnel.core.tunnel.handler

import android.os.PowerManager
import com.zaneschepke.logcatter.LogReader
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelStatus
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.state.FailureReason
import com.zaneschepke.wireguardautotunnel.domain.state.LogHealthState
import com.zaneschepke.wireguardautotunnel.domain.state.PingState
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelState
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelStatistics
import com.zaneschepke.wireguardautotunnel.util.extensions.toMillis
import com.zaneschepke.wireguardautotunnel.util.network.NetworkUtils
import inet.ipaddr.AddressValueException
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import io.ktor.util.collections.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class TunnelMonitorHandler(
    private val activeTunnels: StateFlow<Map<Int, TunnelState>>,
    private val tunnelsRepository: TunnelRepository,
    private val settingsRepository: GeneralSettingRepository,
    private val monitoringSettingsRepository: MonitoringSettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val networkUtils: NetworkUtils,
    private val logReader: LogReader,
    private val powerManager: PowerManager,
    private val getStatistics: (Int) -> TunnelStatistics?,
    private val updateTunnelStatus:
        suspend (
            Int, TunnelStatus?, TunnelStatistics?, Map<String, PingState>?, LogHealthState?,
        ) -> Unit,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val jobs = ConcurrentHashMap<Int, Job>()

    init {
        applicationScope.launch(ioDispatcher) {
            activeTunnels.collect { activeTuns ->
                mutex.withLock {
                    val activeIds = activeTuns.keys.toSet()
                    (jobs.keys - activeIds).forEach { id ->
                        Timber.d("Shutting down tunnel monitoring job for tunnelId: $id")
                        jobs.remove(id)?.cancel()
                    }

                    val tunnels = tunnelsRepository.flow.firstOrNull() ?: return@collect
                    val tunnelsById = tunnels.associateBy { it.id }

                    activeIds.forEach { id ->
                        if (jobs.containsKey(id)) return@forEach
                        val config = tunnelsById[id] ?: return@forEach
                        val settings = settingsRepository.flow.filterNotNull().first()
                        val tunStateFlow =
                            activeTunnels.map { it[id] }.stateIn(applicationScope + ioDispatcher)
                        jobs[id] =
                            applicationScope.launch(ioDispatcher) {
                                Timber.d("Starting tunnel monitoring job for tunnelId: $id")
                                startMonitoring(
                                    config = config,
                                    withLogs = settings.appMode != AppMode.KERNEL,
                                    tunStateFlow = tunStateFlow,
                                    getStatistics = { tunnelId -> getStatistics(tunnelId) },
                                    updateTunnelStatus = { tid, _, stats, pings, logHealth ->
                                        updateTunnelStatus(tid, null, stats, pings, logHealth)
                                    },
                                )
                            }
                    }
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun startMonitoring(
        config: TunnelConfig,
        withLogs: Boolean,
        tunStateFlow: StateFlow<TunnelState?>,
        getStatistics: suspend (Int) -> TunnelStatistics?,
        updateTunnelStatus:
            suspend (
                Int, TunnelStatus?, TunnelStatistics?, Map<String, PingState>?, LogHealthState?,
            ) -> Unit,
    ) = coroutineScope {
        launch { startPingMonitor(config, tunStateFlow, updateTunnelStatus) }
        launch { startWgStatsPoll(config.id, getStatistics, updateTunnelStatus) }
        if (withLogs) launch { startLogsMonitor(config, updateTunnelStatus) }
    }

    private suspend fun startLogsMonitor(
        tunnelConfig: TunnelConfig,
        updateTunnelStatus:
            suspend (
                Int, TunnelStatus?, TunnelStatistics?, Map<String, PingState>?, LogHealthState?,
            ) -> Unit,
    ) {
        logReader.liveLogs
            .filter { log -> log.tag.contains(tunnelConfig.name) }
            .mapNotNull { log ->
                val now = System.currentTimeMillis()

                when {
                    successLogRegex.containsMatchIn(log.message) ->
                        LogHealthState(isHealthy = true, timestamp = now)

                    failureLogRegex.containsMatchIn(log.message) ->
                        LogHealthState(isHealthy = false, timestamp = now)

                    else -> null
                }
            }
            .distinctUntilChangedBy { it.isHealthy }
            .collect { logHealthState ->
                Timber.d("Tunnel log health updated for ${tunnelConfig.name}: $logHealthState")
                updateTunnelStatus(tunnelConfig.id, null, null, null, logHealthState)
            }
    }

    private suspend fun startPingMonitor(
        tunnelConfig: TunnelConfig,
        tunStateFlow: StateFlow<TunnelState?>,
        updateTunnelStatus:
            suspend (
                Int, TunnelStatus?, TunnelStatistics?, Map<String, PingState>?, LogHealthState?,
            ) -> Unit,
    ) = coroutineScope {
        val pingStatsFlow = MutableStateFlow<Map<String, PingState>>(emptyMap())

        val connectivityStateFlow = networkMonitor.connectivityStateFlow.stateIn(this)

        val isNetworkConnected = connectivityStateFlow.map { it.hasInternet() }.stateIn(this)

        combine(
                settingsRepository.flow.distinctUntilChangedBy { it.appMode },
                monitoringSettingsRepository.flow,
            ) { settings, monitorSettings ->
                Pair(settings.appMode, monitorSettings)
            }
            .collectLatest { (appMode, settings) ->
                if (!settings.isPingEnabled) return@collectLatest
                // TODO for now until we get monitoring for these modes
                if (appMode == AppMode.LOCK_DOWN || appMode == AppMode.PROXY) return@collectLatest

                Timber.d("Starting pinger for ${tunnelConfig.name} with settings")

                val config = tunnelConfig.toAmConfig()

                val pingablePeers = config.peers.filter { it.allowedIps.isNotEmpty() }
                if (pingablePeers.isEmpty()) return@collectLatest

                suspend fun performPing() {
                    val updates = ConcurrentMap<String, PingState>()

                    pingablePeers
                        .map { it.publicKey.toBase64() to it }
                        .forEach { (key, peer) ->
                            ensureActive()
                            val previousState = pingStatsFlow.value[key] ?: PingState()

                            val allowedIpStr = peer.allowedIps.firstOrNull()?.toString()
                            if (allowedIpStr == null) {
                                updates[key] =
                                    previousState.copy(
                                        isReachable = false,
                                        failureReason = FailureReason.NoResolvedEndpoint,
                                        lastPingAttemptMillis = System.currentTimeMillis(),
                                    )
                                return@forEach
                            }

                            val host =
                                tunnelConfig.pingTarget
                                    ?: run {
                                        val parts = allowedIpStr.split("/")
                                        val internalIp =
                                            if (parts.size == 2) parts[0] else allowedIpStr
                                        val prefix =
                                            if (parts.size == 2) parts[1].toIntOrNull() ?: 32
                                            else 32
                                        val cleanedIp = internalIp.removeSurrounding("[", "]")
                                        val defaultCloudflare =
                                            if (cleanedIp.contains(":")) CLOUDFLARE_IPV6_IP
                                            else CLOUDFLARE_IPV4_IP

                                        if (prefix <= 1) {
                                            defaultCloudflare
                                        } else {
                                            try {
                                                val addrStr = IPAddressString(cleanedIp)
                                                val addr: IPAddress =
                                                    addrStr.address
                                                        ?: throw AddressValueException(
                                                            "Invalid IP: $cleanedIp"
                                                        )
                                                val isIpv6 = addr.isIPv6
                                                val cloudflareIp =
                                                    if (isIpv6) CLOUDFLARE_IPV6_IP
                                                    else CLOUDFLARE_IPV4_IP
                                                val max = if (isIpv6) 128 else 32

                                                if (prefix == max) {
                                                    addr.toCanonicalString()
                                                } else {
                                                    val nextAddr: IPAddress? = addr.increment(1)
                                                    nextAddr?.toCanonicalString() ?: cloudflareIp
                                                }
                                            } catch (e: AddressValueException) {
                                                Timber.e(
                                                    e,
                                                    "Failed to parse or increment IP: $cleanedIp",
                                                )
                                                defaultCloudflare
                                            }
                                        }
                                    }

                            val attemptTime = System.currentTimeMillis()
                            val timeout = settings.tunnelPingTimeoutSeconds?.toMillis() ?: 5000L
                            runCatching {
                                    withTimeout(
                                        settings.tunnelPingTimeoutSeconds?.toMillis() ?: 5000L
                                    ) {
                                        val pingStats =
                                            settings.tunnelPingTimeoutSeconds?.let {
                                                networkUtils.pingWithStats(
                                                    host,
                                                    settings.tunnelPingAttempts,
                                                    it.toMillis(),
                                                )
                                            }
                                                ?: networkUtils.pingWithStats(
                                                    host,
                                                    settings.tunnelPingAttempts,
                                                )

                                        updates[key] =
                                            previousState.copy(
                                                transmitted = pingStats.transmitted,
                                                received = pingStats.received,
                                                packetLoss = pingStats.packetLoss,
                                                rttMin = pingStats.rttMin,
                                                rttMax = pingStats.rttMax,
                                                rttAvg = pingStats.rttAvg,
                                                rttStddev = pingStats.rttStddev,
                                                isReachable = pingStats.isReachable,
                                                failureReason =
                                                    if (pingStats.isReachable) null
                                                    else FailureReason.PingFailed,
                                                lastSuccessfulPingMillis =
                                                    pingStats.lastSuccessfulPingMillis
                                                        ?: previousState.lastSuccessfulPingMillis,
                                                pingTarget = host,
                                                lastPingAttemptMillis = attemptTime,
                                            )
                                        Timber.d(
                                            "Ping completed for peer ${peer.publicKey.toBase64().substring(0, 5)}.. to host $host with stats: $pingStats"
                                        )
                                    }
                                }
                                .onFailure {
                                    Timber.e(
                                        it,
                                        "Ping failed for peer ${peer.publicKey} in ${tunnelConfig.name} to host $host",
                                    )
                                    updates[key] =
                                        previousState.copy(
                                            isReachable = false,
                                            failureReason = FailureReason.PingFailed,
                                            pingTarget = host,
                                            lastPingAttemptMillis = attemptTime,
                                        )
                                }
                        }

                    if (updates.isNotEmpty()) {
                        ensureActive()
                        pingStatsFlow.update { updates }
                        updateTunnelStatus(tunnelConfig.id, null, null, updates, null)
                    }
                }

                // Wait for the tunnel to be fully active
                tunStateFlow.filter { state -> state?.status is TunnelStatus.Up }.first()

                // small delay to make sure tunnel is fully up before we actively monitor
                delay(PING_MONITOR_START_DELAY)

                while (isActive) {
                    ensureActive()
                    if (!powerManager.isDeviceIdleMode) {
                        if (isNetworkConnected.value) {
                            performPing()
                        } else {
                            pingStatsFlow.update { current ->
                                current.mapValues { entry ->
                                    entry.value.copy(
                                        isReachable = false,
                                        failureReason = FailureReason.NoConnectivity,
                                        lastPingAttemptMillis = System.currentTimeMillis(),
                                    )
                                }
                            }
                            ensureActive()
                            updateTunnelStatus(
                                tunnelConfig.id,
                                null,
                                null,
                                pingStatsFlow.value,
                                null,
                            )
                        }
                    }
                    delay(settings.tunnelPingIntervalSeconds.toMillis())
                }
            }
    }

    private suspend fun startWgStatsPoll(
        tunnelId: Int,
        getStatistics: suspend (Int) -> TunnelStatistics?,
        updateTunnelStatus:
            suspend (
                Int, TunnelStatus?, TunnelStatistics?, Map<String, PingState>?, LogHealthState?,
            ) -> Unit,
    ) = coroutineScope {
        while (isActive) {
            ensureActive()
            if (!powerManager.isDeviceIdleMode) {
                val stats = getStatistics(tunnelId)
                ensureActive()
                updateTunnelStatus(tunnelId, null, stats, null, null)
            }
            delay(STATS_DELAY)
        }
    }

    companion object {
        private val successLogRegex =
            Regex("Received handshake response|Receiving keepalive packet", RegexOption.IGNORE_CASE)

        private val failureLogRegex =
            Regex(
                "Failed to send handshake initiation: write udp|" +
                    "Handshake did not complete after 5 seconds, retrying|" +
                    "Failed to send data packets",
                RegexOption.IGNORE_CASE,
            )

        const val CLOUDFLARE_IPV6_IP = "2606:4700:4700::1111"
        const val CLOUDFLARE_IPV4_IP = "1.1.1.1"
        const val STATS_DELAY = 1_000L
        const val PING_MONITOR_START_DELAY = 5_000L
    }
}
