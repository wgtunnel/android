package com.zaneschepke.tunnel.service

import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.ServiceCompat
import com.zaneschepke.hevtunnel.HevTunnelConfig
import com.zaneschepke.hevtunnel.TProxyService
import com.zaneschepke.tunnel.ProxyBackend
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.backend.KillSwitch
import com.zaneschepke.tunnel.backend.ServiceHolder
import com.zaneschepke.tunnel.backend.ServiceHolder.Companion.DEFAULT_MTU
import com.zaneschepke.tunnel.backend.ServiceHolder.Companion.alwaysOnCallback
import com.zaneschepke.tunnel.backend.ServiceHolder.Companion.vpnService
import com.zaneschepke.tunnel.backend.SocketProtector
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.KillSwitchConfig
import com.zaneschepke.tunnel.util.parseDns
import com.zaneschepke.tunnel.util.parseInetNetwork
import com.zaneschepke.wireguardautotunnel.parser.Config
import java.io.IOException
import java.net.ServerSocket
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class VpnService : android.net.VpnService(), KillSwitch, SocketProtector {

    private val backend: Backend by inject(Backend::class.java)
    private val serviceHolder: ServiceHolder by inject(ServiceHolder::class.java)

    private val defaultPass = UUID.randomUUID().toString()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var hevBridgeJob: Job? = null
    private var fd: ParcelFileDescriptor? = null

    val builder: Builder
        get() = Builder()

    override fun onCreate() {
        vpnService.complete(this)
        // We call this for all backend modes as it is shared for bootstrapping bypass
        ProxyBackend.setSocketProtector(this)
        serviceHolder.ensureNativeCallbacksRegistered()
        launchForegroundNotification()
        super.onCreate()
    }

    fun launchForegroundNotification() {
        ServiceCompat.startForeground(
            this,
            backend.notificationProvider.vpnNotificationId,
            backend.notificationProvider.vpnInitNotification,
            SYSTEM_EXEMPT_SERVICE_TYPE_ID,
        )
    }

    override fun onDestroy() {
        Timber.d("VpnService destroyed")

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        ProxyBackend.setSocketProtector(null)

        disableKillSwitch()
        hevBridgeJob?.cancel()

        serviceScope.cancel()

        runBlocking {
            backend.stopAllOfType(BackendMode.Vpn::class)
            backend.stopAllOfType(BackendMode.Proxy.KillSwitchPrimary::class)
        }

        serviceHolder.clear(this)

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        vpnService.complete(this)
        launchForegroundNotification()

        // Service restarted by system or Always-on VPN started
        if (
            intent == null ||
                intent.component == null ||
                (intent.component!!.packageName != packageName)
        ) {
            Timber.d("VpnService started by system")
            alwaysOnCallback?.get()?.alwaysOnTriggered()
        }
        return START_STICKY
    }

    private fun startHevBridge(): Job {
        val job = serviceScope.launch {
            try {
                val port = getAvailablePort()
                val fd = fd ?: throw IOException("No VPN interface fd available")
                val config =
                    HevTunnelConfig(
                        port = port,
                        mtu = DEFAULT_MTU,
                        ipv4 = IPV4_INTERFACE_ADDRESS,
                        ipv6 = IPV6_INTERFACE_ADDRESS,
                        address = LOCALHOST,
                        username = DEFAULT_USERNAME,
                        password = defaultPass,
                    )
                val hevConfigFile = TProxyService.createHevTunnelConfig(config, this@VpnService)
                TProxyService.TProxyStartService(hevConfigFile.absolutePath, fd.fd)
            } catch (e: IOException) {
                Timber.e(e)
            }
        }
        job.invokeOnCompletion {
            TProxyService.TProxyStopService()
            hevBridgeJob = null
        }
        return job
    }

    @Throws(IOException::class)
    private fun getAvailablePort(): Int {
        ServerSocket(0).use { socket ->
            socket.setReuseAddress(true)
            return socket.getLocalPort()
        }
    }

    private fun disableKillSwitch() {
        fd?.close()
        fd = null
    }

    override fun setKillSwitch(config: KillSwitchConfig?) {
        if (config == null) return disableKillSwitch()
        fd =
            builder
                .apply {
                    setSession(LOCKDOWN_SESSION_NAME)
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
                        builder.setMetered(config.metered)
                    }
                    addRoute(IPV6_DEFAULT_ROUTE, 0)
                    setMtu(DEFAULT_MTU)
                    addDnsServer(DEFAULT_DNS_SERVER)
                }
                .establish()
    }

    fun createTunInterface(tunnel: Tunnel, config: Config): ParcelFileDescriptor? {
        return builder
            .apply {
                setSession(tunnel.name)

                config.`interface`.includedApplications?.forEach { addAllowedApplication(it) }
                config.`interface`.excludedApplications?.forEach { addDisallowedApplication(it) }

                config.`interface`.address?.split(",")?.forEach { rawAddress ->
                    val (address, prefixLength) = rawAddress.parseInetNetwork()
                    addAddress(address, prefixLength)
                }

                config.`interface`.dns?.let { rawDns ->
                    val dnsConfig = rawDns.parseDns()
                    dnsConfig.dnsServers.forEach { addDnsServer(it) }
                    dnsConfig.searchDomains.forEach { addSearchDomain(it) }
                }

                config.peers.forEach { peer ->
                    peer.allowedIPs?.split(",")?.forEach { entry ->
                        val (address, prefix) = entry.parseInetNetwork()
                        Timber.d("Adding route from config: $address/$prefix")
                        addRoute(address, prefix)
                    }
                }

                allowFamily(OsConstants.AF_INET)
                allowFamily(OsConstants.AF_INET6)

                val mtu = config.`interface`.mtu ?: DEFAULT_MTU
                setMtu(mtu)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setMetered(tunnel.isMetered)
                }

                setUnderlyingNetworks(null)
                setBlocking(true)
            }
            .establish()
    }

    override fun startHevSocks5Bridge() {
        if (hevBridgeJob != null) return
        hevBridgeJob = startHevBridge()
    }

    override fun stopHevSocks5Bridge() {
        hevBridgeJob?.cancel()
        hevBridgeJob = null
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
        private const val IPV6_INTERFACE_ADDRESS = "2001:db8::1"
        private const val DEFAULT_USERNAME = "local"
        private const val IPV4_DEFAULT_ROUTE = "0.0.0.0"
        private const val IPV6_DEFAULT_ROUTE = "::"
        private const val DEFAULT_DNS_SERVER = "1.1.1.1"

        private const val SYSTEM_EXEMPT_SERVICE_TYPE_ID = 1 shl 10
    }
}
