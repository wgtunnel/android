package com.zaneschepke.wireguardautotunnel.core.tunnel.handler

import android.os.PowerManager
import com.zaneschepke.networkmonitor.ActiveNetwork
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelState
import com.zaneschepke.wireguardautotunnel.util.extensions.isValidIpv4orIpv6Address
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.amnezia.awg.config.InetEndpoint
import timber.log.Timber

/**
 * Handles WiFi roaming (BSSID change on same SSID).
 *
 * Everything else is handled by AutoTunnel:
 * - 4G → WiFi transitions
 * - WiFi → 4G transitions
 * - SSID changes (different network)
 * - Ethernet transitions
 */
class WifiRoamingHandler(
    private val activeTunnels: StateFlow<Map<Int, TunnelState>>,
    private val settingsRepository: GeneralSettingRepository,
    private val networkMonitor: NetworkMonitor,
    private val powerManager: PowerManager,
    private val forceSocketRebind: suspend (TunnelConfig) -> Boolean,
    private val getTunnelConfig: suspend (Int) -> TunnelConfig?,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    // State tracking
    private var lastBssid: String? = null
    private var lastSsid: String? = null

    // Recovery job management (thread-safe)
    private var pendingRecoveryJob: Job? = null
    private val jobMutex = Mutex()

    // Cache: tunnel ID -> (hostname -> resolved IP) for DDNS
    private val endpointIpCache = ConcurrentHashMap<Int, Map<String, String>>()

    init {
        applicationScope.launch(ioDispatcher) { monitorBssidChanges() }
        applicationScope.launch(ioDispatcher) { maintainEndpointCache() }
    }

    /**
     * Monitors BSSID changes. Only triggers roaming when:
     * - Was already on WiFi (prevBssid != null)
     * - Same SSID (not a network switch)
     * - Different valid BSSID
     */
    private suspend fun monitorBssidChanges() {
        networkMonitor.connectivityStateFlow
            .map { state ->
                (state.activeNetwork as? ActiveNetwork.Wifi)?.let { WifiInfo(it.ssid, it.bssid) }
            }
            .distinctUntilChanged()
            .collect { wifi ->
                if (wifi == null) {
                    // Not on WiFi: cancel pending recovery but keep lastBssid/lastSsid
                    // so we detect roaming when WiFi reconnects to a different AP
                    cancelRecovery()
                    return@collect
                }

                val prevBssid = lastBssid
                val prevSsid = lastSsid

                // Always update state first
                lastBssid = wifi.bssid
                lastSsid = wifi.ssid

                // SSID changed: cancel recovery, let AutoTunnel handle
                if (prevSsid != null && prevSsid != wifi.ssid) {
                    Timber.d(
                        "SSID changed: %s -> %s, letting AutoTunnel handle",
                        prevSsid,
                        wifi.ssid,
                    )
                    cancelRecovery()
                    return@collect
                }

                // Roaming: same SSID + different BSSID + was on WiFi
                if (
                    prevBssid != null &&
                        wifi.bssid != null &&
                        prevBssid != wifi.bssid &&
                        isValidBssid(prevBssid) &&
                        isValidBssid(wifi.bssid)
                ) {
                    Timber.i("Roaming detected: %s -> %s", prevBssid, wifi.bssid)
                    onRoamingDetected()
                }
            }
    }

    /**
     * Maintains IP cache for DDNS endpoints. DNS may fail during roaming (broken tunnel), so we
     * cache IPs beforehand. Cache is cleaned when tunnel goes DOWN or is removed. Also resets BSSID
     * tracking state when all tunnels go DOWN. This prevents false roaming.
     */
    private suspend fun maintainEndpointCache() {
        var hadActiveTunnels = false
        activeTunnels.collect { tunnelMap ->
            val hasActiveTunnels = tunnelMap.values.any { it.status.isUp() }

            when {
                // All tunnels just went DOWN: clear BSSID
                hadActiveTunnels && !hasActiveTunnels -> {
                    lastBssid = null
                    lastSsid = null
                    Timber.d("All tunnels DOWN: BSSID cleared")
                }
                !hadActiveTunnels && hasActiveTunnels -> {
                    val wifi =
                        networkMonitor.connectivityStateFlow.first().activeNetwork
                            as? ActiveNetwork.Wifi
                    lastBssid = wifi?.bssid
                    lastSsid = wifi?.ssid
                    Timber.d("First tunnel UP : cached BSSID to %s", lastBssid)
                }
            }
            hadActiveTunnels = hasActiveTunnels

            // Clean cache for tunnels that are DOWN or removed
            endpointIpCache.keys.toList().forEach { id ->
                val state = tunnelMap[id]
                if (state == null || !state.status.isUp()) {
                    endpointIpCache.remove(id)
                    Timber.d("Cache cleared for tunnel %d", id)
                }
            }

            // Cache active tunnels with DDNS
            for ((id, state) in tunnelMap) {
                if (!state.status.isUp() || endpointIpCache.containsKey(id)) continue
                getTunnelConfig(id)?.let { cacheEndpointIps(id, it) }
            }
        }
    }

    private fun cacheEndpointIps(id: Int, config: TunnelConfig) {
        val cache = mutableMapOf<String, String>()
        val isIpv4Preferred = config.isIpv4Preferred
        for (peer in config.toAmConfig().peers) {
            val host = peer.endpoint.orElse(null)?.host ?: continue
            if (host.isValidIpv4orIpv6Address()) continue

            runCatching {
                val addresses = InetAddress.getAllByName(host)
                val preferred =
                    if (isIpv4Preferred) {
                        addresses.filterIsInstance<Inet4Address>().firstOrNull()
                            ?: addresses.firstOrNull()
                    } else {
                        addresses.firstOrNull()
                    }
                preferred?.hostAddress?.let {
                    cache[host] = it
                    Timber.d("Cached: %s -> %s", host, it)
                }
            }
        }
        if (cache.isNotEmpty()) endpointIpCache[id] = cache
    }

    private suspend fun onRoamingDetected() {
        // Skip kernel mode - handles rebinding natively
        val settings = settingsRepository.flow.filterNotNull().first()
        if (settings.appMode == AppMode.KERNEL) {
            Timber.d("Kernel mode: skipping roaming handler")
            return
        }

        val tunnelIds = activeTunnels.value.filter { it.value.status.isUp() }.keys
        if (tunnelIds.isEmpty()) return

        // Thread-safe job management with debounce
        jobMutex.withLock {
            val wasAlreadyPending = pendingRecoveryJob?.isActive == true
            pendingRecoveryJob?.cancel()

            pendingRecoveryJob =
                applicationScope.launch(ioDispatcher) {
                    val wakeLock = acquireWakeLock()
                    try {
                        if (wasAlreadyPending) {
                            Timber.d("Rapid roaming, debouncing %dms", DEBOUNCE_MS)
                            delay(DEBOUNCE_MS)

                            // Verify still on WiFi after debounce
                            if (
                                networkMonitor.connectivityStateFlow.first().activeNetwork
                                    !is ActiveNetwork.Wifi
                            ) {
                                Timber.d("No longer on WiFi, skipping recovery")
                                return@launch
                            }
                        }

                        // Recover all active tunnels
                        val currentTunnels =
                            activeTunnels.value.filter { it.value.status.isUp() }.keys
                        for (id in currentTunnels) {
                            val config = getTunnelConfig(id) ?: continue
                            recoverTunnel(id, config)
                        }
                    } finally {
                        wakeLock?.let { if (it.isHeld) it.release() }
                    }
                }
        }
    }

    private suspend fun recoverTunnel(id: Int, config: TunnelConfig) {
        Timber.d("Recovering tunnel: %s", config.name)

        // Use cached IPs to avoid DNS through broken tunnel
        val cachedConfig =
            endpointIpCache[id]?.let { cache ->
                if (cache.isNotEmpty()) {
                    Timber.d("Using cached IPs for config update")
                    applyIpCache(config, cache)
                } else config
            } ?: config

        // Phase 1: hot config update with cached IPs (zero downtime)
        // setState(tunnel, UP, newConfig) updates endpoints without tearing down tun0
        val cacheOk = runCatching { forceSocketRebind(cachedConfig) }.getOrDefault(false)
        if (cacheOk) {
            Timber.i("Tunnel %s recovered via cached config update", config.name)
            cacheEndpointIps(id, config)
            return
        }

        // Phase 2: fresh DNS resolution + hot config update (zero downtime)
        // DNS resolves through system resolver (WiFi), not the broken tunnel
        Timber.w("Cached config update failed, trying fresh DNS resolution")
        cacheEndpointIps(id, config)
        val freshConfig =
            endpointIpCache[id]?.let { cache ->
                if (cache.isNotEmpty()) applyIpCache(config, cache) else config
            } ?: config

        val freshOk = runCatching { forceSocketRebind(freshConfig) }.getOrDefault(false)
        if (freshOk) {
            Timber.i("Tunnel %s recovered via fresh DNS config update", config.name)
            return
        }

        Timber.e("All recovery attempts failed for tunnel %s", config.name)
    }

    private fun applyIpCache(config: TunnelConfig, cache: Map<String, String>): TunnelConfig {
        fun replaceEndpointLine(line: String): String {
            if (!line.trim().startsWith("Endpoint", ignoreCase = true)) return line
            val eqIdx = line.indexOf('=')
            if (eqIdx == -1) return line
            val prefix = line.substring(0, eqIdx + 1)
            val value = line.substring(eqIdx + 1).trim()
            return try {
                val endpoint = InetEndpoint.parse(value)
                val ip = cache[endpoint.host] ?: return line
                val newValue =
                    if (ip.contains(':')) "[$ip]:${endpoint.port}" else "$ip:${endpoint.port}"
                "$prefix $newValue"
            } catch (e: Exception) {
                line
            }
        }

        fun replace(text: String) =
            text.lines().joinToString("\n") { line -> replaceEndpointLine(line) }

        return config.copy(
            amQuick = replace(config.amQuick.ifBlank { config.wgQuick }),
            wgQuick = replace(config.wgQuick),
        )
    }

    private fun cancelRecovery() {
        // Note: Called from collect{} which is sequential, no race with itself
        // pendingRecoveryJob?.cancel() is thread-safe
        pendingRecoveryJob?.cancel()
        pendingRecoveryJob = null
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock(): PowerManager.WakeLock? {
        return runCatching {
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wgtunnel:roaming").apply {
                    acquire(WAKELOCK_TIMEOUT_MS)
                }
            }
            .onFailure { Timber.e(it, "Failed to acquire WakeLock") }
            .getOrNull()
    }

    private fun isValidBssid(bssid: String) =
        bssid.isNotBlank() && bssid !in INVALID_BSSIDS && bssid.matches(BSSID_REGEX)

    private data class WifiInfo(val ssid: String, val bssid: String?)

    companion object {
        private val INVALID_BSSIDS =
            setOf("02:00:00:00:00:00", "00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff")
        private val BSSID_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        private const val WAKELOCK_TIMEOUT_MS = 15_000L
        private const val DEBOUNCE_MS = 2_000L // Rapid roaming: wait for BSSID to settle
    }
}
