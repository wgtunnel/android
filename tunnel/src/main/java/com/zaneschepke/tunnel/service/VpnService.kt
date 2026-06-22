package com.zaneschepke.tunnel.service

import android.content.Intent
import android.net.TrafficStats
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import com.zaneschepke.hevtunnel.HevTunnelConfig
import com.zaneschepke.hevtunnel.TProxyService
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.backend.KillSwitch
import com.zaneschepke.tunnel.backend.SocketProtector
import com.zaneschepke.tunnel.model.KillSwitchConfig
import com.zaneschepke.tunnel.service.ServiceHolder.Companion.DEFAULT_MTU
import com.zaneschepke.tunnel.service.ServiceHolder.Companion.alwaysOnCallback
import com.zaneschepke.tunnel.util.parseDns
import com.zaneschepke.tunnel.util.parseInetNetwork
import com.zaneschepke.wireguardautotunnel.parser.Config
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class VpnService : android.net.VpnService(), KillSwitch, SocketProtector {

    private val backend: Backend by inject(Backend::class.java)
    private val serviceHolder: ServiceHolder by inject(ServiceHolder::class.java)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val shutdownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var userActivatedShutdown = false
    private var hevBridgeJob: Job? = null
    @Volatile private var fd: ParcelFileDescriptor? = null

    override fun onCreate() {
        serviceHolder.set(this)
        super.onCreate()
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun onDestroy() {
        Timber.d("VpnService destroyed")
        try {
            serviceHolder.clearVpnService()

            // Stop the companion foreground service alongside the VPN teardown
            stopService(Intent(this, VpnCompanionService::class.java))

            disableKillSwitch()
            hevBridgeJob?.cancel()
            serviceScope.cancel()
            stopHevSocks5Bridge()
            if (!userActivatedShutdown) {
                Timber.d("Service being killed by system, clean up tunnels")
                shutdownScope.launch { backend.stopAllActiveTunnels() }
            }
        } finally {
            super.onDestroy()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun shutdown() {
        userActivatedShutdown = true
        stopSelf()
    }

    override fun onRevoke() {
        Timber.w("VPN privilege revoked by system")
        userActivatedShutdown = false
        disableKillSwitch()
        stopHevSocks5Bridge()
        serviceScope.launch { backend.stopAllActiveTunnels() }
        stopSelf()
        super.onRevoke()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceHolder.set(this)

        // Ensure the companion service is up immediately to provide foreground process
        bootKeepaliveService()

        // Service restarted by system or Always-on VPN started
        if (
            intent == null ||
                intent.component == null ||
                (intent.component!!.packageName != packageName)
        ) {
            Timber.d("VpnService started by system (Always-On trigger)")
            alwaysOnCallback?.get()?.alwaysOnTriggered()
        }
        return START_STICKY
    }

    private fun bootKeepaliveService() {
        try {
            val intent = Intent(this, VpnCompanionService::class.java)
            // Works for starts and within the temporary AOVPN boot window
            startForegroundService(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start companion keepalive service")
        }
    }

    private fun startHevBridge(port: Int, pass: String): Job {
        val job = serviceScope.launch {
            TrafficStats.setThreadStatsTag(HEV_BRIDGE_TRAFFIC_TAG)
            try {
                val vpnFd = fd ?: throw IOException("No VPN interface fd available")

                repeat(60) { attempt ->
                    try {
                        java.net.Socket().use { socket ->
                            socket.connect(java.net.InetSocketAddress(LOCALHOST, port), 800)
                        }

                        Timber.d(
                            "SOCKS5 proxy is ready on port $port, starting HEV bridge (attempt ${attempt + 1})"
                        )

                        val config =
                            HevTunnelConfig(
                                port = port,
                                mtu = DEFAULT_MTU,
                                ipv4 = IPV4_INTERFACE_ADDRESS,
                                ipv6 = IPV6_INTERFACE_ADDRESS,
                                address = LOCALHOST,
                                username = LOCKDOWN_USERNAME,
                                password = pass,
                            )
                        val hevConfigFile =
                            TProxyService.createHevTunnelConfig(config, this@VpnService.cacheDir)
                        TProxyService.TProxyStartService(hevConfigFile.absolutePath, vpnFd.fd)

                        Timber.d("HEV bridge started successfully - coroutine can now exit")
                        return@launch // safe to exit as hev handles own threading internally
                    } catch (e: Exception) {
                        Timber.w(e, "SOCKS5 connect failed (attempt ${attempt + 1})")
                        if (attempt % 5 == 0) {
                            Timber.d("SOCKS5 not ready yet, retrying...")
                        }
                        delay(300.milliseconds)
                    }
                }
                Timber.e("Timed out waiting for SOCKS5 proxy to be ready")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start HEV bridge")
            } finally {
                TrafficStats.clearThreadStatsTag()
            }
        }

        // stop HEV when the job is canceled from stopHevSocks5Bridge or onDestroy
        job.invokeOnCompletion { cause ->
            if (cause != null) { // canceled or failed
                Timber.d("HEV bridge job stopped - shutting down native HEV")
                TProxyService.TProxyStopService()
            }
            hevBridgeJob = null
        }

        return job
    }

    private fun disableKillSwitch() {
        fd?.close()
        fd = null
    }

    override fun setKillSwitch(config: KillSwitchConfig?) {
        if (config == null) return disableKillSwitch()
        fd?.close()
        val intent = backend.applicationProvider.createVpnConfigurePendingIntent(this@VpnService)
        fd =
            Builder()
                .apply {
                    setSession(LOCKDOWN_SESSION_NAME)
                    setConfigureIntent(intent)
                    addAddress(IPV4_INTERFACE_ADDRESS, 32)
                    if (config.dualStack) addAddress(IPV6_INTERFACE_ADDRESS, 128)
                    if (config.allowedIps.isEmpty()) {
                        addRoute(IPV4_DEFAULT_ROUTE, 0)
                    } else {
                        config.allowedIps.forEach { net ->
                            Timber.d("Adding allowedIp to kill switch: $net")
                            val (address, prefix) = net.parseInetNetwork()
                            addRoute(address, prefix)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setMetered(config.metered)
                    }
                    addAddress(IPV6_ULA, 128)
                    addRoute(IPV6_DEFAULT_ROUTE, 0)
                    setMtu(DEFAULT_MTU)
                    addDnsServer(DEFAULT_DNS_SERVER)
                }
                .establish()
    }

    fun createTunInterface(tunnel: Tunnel, config: Config): ParcelFileDescriptor? {
        val intent = backend.applicationProvider.createVpnConfigurePendingIntent(this@VpnService)
        return Builder()
            .apply {
                setSession(tunnel.name)
                setConfigureIntent(intent)
                setMtu(config.`interface`.mtu ?: DEFAULT_MTU)
                setBlocking(true)
                setUnderlyingNetworks(null)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setMetered(tunnel.isMetered)
                }

                config.`interface`.includedApplications?.forEach { addAllowedApplication(it) }
                config.`interface`.excludedApplications?.forEach { addDisallowedApplication(it) }

                var hasIpv4 = false
                var hasIpv6 = false
                var sawDefaultRoute = false

                // Parse interface addresses
                config.`interface`.address?.split(",")?.forEach { rawAddress ->
                    val (address, prefixLength) = rawAddress.parseInetNetwork()
                    addAddress(address, prefixLength)
                    if (address is Inet4Address) hasIpv4 = true else hasIpv6 = true
                }

                // Parse peer routes
                config.peers.forEach { peer ->
                    peer.allowedIPs
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.forEach { entry ->
                            val (address, prefix) = entry.parseInetNetwork()
                            addRoute(address, prefix)

                            if (prefix == 0) {
                                sawDefaultRoute = true
                            }
                            if (address is Inet4Address) hasIpv4 = true else hasIpv6 = true
                        }
                }

                // "Kill-switch" semantics (mirrors wireguard-android)
                val isKillSwitchRouting = sawDefaultRoute && config.peers.size == 1

                if (!isKillSwitchRouting) {
                    allowFamily(OsConstants.AF_INET)
                    allowFamily(OsConstants.AF_INET6)
                }

                // Only add DNS servers whose family is supported
                config.`interface`.dns?.let { rawDns ->
                    val dnsConfig = rawDns.parseDns()
                    dnsConfig.dnsServers.forEach { dnsServer ->
                        val isIpv6 = dnsServer is Inet6Address
                        if ((isIpv6 && hasIpv6) || (!isIpv6 && hasIpv4)) {
                            addDnsServer(dnsServer)
                        } else {
                            Timber.w(
                                "Dropped DNS server $dnsServer: IP family not allowed by interface/routes"
                            )
                        }
                    }
                    dnsConfig.searchDomains.forEach { addSearchDomain(it) }
                }
            }
            .establish()
    }

    override fun startHevSocks5Bridge(port: Int, pass: String) {
        if (hevBridgeJob != null) return
        hevBridgeJob = startHevBridge(port, pass)
    }

    override fun stopHevSocks5Bridge() {
        hevBridgeJob?.cancel()
        hevBridgeJob = null

        try {
            TProxyService.TProxyStopService()
        } catch (e: Exception) {
            Timber.w(e, "TProxyStopService failed, may already be stopped")
        }
    }

    override fun bypass(fd: Int): Int {
        Timber.d("Bypassing VPN fd: $fd")
        val bypassed =
            try {
                if (protect(fd)) 1 else 0
            } catch (e: Exception) {
                Timber.e(e, "Failed to protect VPN fd")
                0
            }
        Timber.d("Socket protected result: $fd")
        return bypassed
    }

    interface AlwaysOnCallback {
        fun alwaysOnTriggered()
    }

    companion object {
        private const val LOCKDOWN_SESSION_NAME = "Lockdown"
        private const val LOCALHOST = "127.0.0.1"
        private const val IPV4_INTERFACE_ADDRESS = "10.0.0.1"
        private const val IPV6_ULA = "fd00::1"
        private const val IPV6_INTERFACE_ADDRESS = "2001:db8::1"
        const val LOCKDOWN_USERNAME = "local"
        private const val IPV4_DEFAULT_ROUTE = "0.0.0.0"
        private const val IPV6_DEFAULT_ROUTE = "::"
        private const val DEFAULT_DNS_SERVER = "1.1.1.1"
        const val HEV_BRIDGE_TRAFFIC_TAG = 0xF00D
    }
}
