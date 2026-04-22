package com.zaneschepke.tunnel.backend

import android.content.Context
import android.content.Intent
import com.getkeepsafe.relinker.ReLinker
import com.topjohnwu.superuser.Shell
import com.zaneschepke.tunnel.DnsConfigManager
import com.zaneschepke.tunnel.ProxyBackend
import com.zaneschepke.tunnel.StatusCallback
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.VpnBackend
import com.zaneschepke.tunnel.features.ActiveConfigFeature
import com.zaneschepke.tunnel.features.PingFeature
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.DnsBoostrapConfig
import com.zaneschepke.tunnel.model.KillSwitchConfig
import com.zaneschepke.tunnel.model.RunningTunnel
import com.zaneschepke.tunnel.model.ScriptDirection
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.tunnel.state.KillSwitchState
import com.zaneschepke.tunnel.state.TunnelStatus
import com.zaneschepke.tunnel.util.BackendException
import com.zaneschepke.tunnel.util.RootShellException
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.parser.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class TunnelBackend(private val context: Context) : Backend {
    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val statusChannel = Channel<TunnelStatus>(Channel.BUFFERED)

    private val statusCallback = StatusCallback { handle, interfaceName, statusCode ->
        Timber.d("Native Callback - Handle: $handle, Interface: $interfaceName, Code: $statusCode")
        statusChannel.trySend(TunnelStatus(handle, interfaceName, statusCode))
    }

    private val tunnelMutex = Mutex()
    private val runningTunnels = ConcurrentHashMap<Int, RunningTunnel>()

    private val rootShell = RootShell(context)
    private val uapiPath = context.dataDir.absolutePath;

    init {
        ReLinker.loadLibrary(context, "am-go")
        backendScope.launch {
            statusChannel.consumeAsFlow().collect { tunnelStatus ->
                val tunnelState = tunnelStatus.asTunnelState()
                val runningTunnel = runningTunnels.values.firstOrNull { it.handle == tunnelStatus.handle } ?: return@collect
                Timber.d("Tunnel ${runningTunnel.tunnel.name} status update: $tunnelStatus -> $tunnelState")
                val currentState = _status.value.activeTunnels[runningTunnel.tunnel.id] ?: return@collect

                _status.update {
                    it.copy(
                        activeTunnels = it.activeTunnels +
                                (runningTunnel.tunnel.id to currentState.copy(state = tunnelState))
                    )
                }
                runningTunnel.tunnel.updateState(tunnelState)
            }
        }
    }

    private val _status = MutableStateFlow(BackendStatus())
    override val status: Flow<BackendStatus> = _status.asStateFlow()

    private fun getActiveConfigInner(runningTunnel: RunningTunnel) : String? {
        return when(runningTunnel.mode) {
            is BackendMode.Kernel -> getActiveKernelConfig(runningTunnel.interfaceName)
            is BackendMode.Proxy.Standard, is BackendMode.Proxy.KillSwitchPrimary -> ProxyBackend.awgGetProxyConfig(runningTunnel.handle)
            is BackendMode.Vpn -> VpnBackend.awgGetConfig(runningTunnel.handle)
        }
    }

    private fun nextInterfaceName(prefix: String = WGT_INTERFACE_PREFIX): String {
        val existing = runningTunnels.values
            .map { it.interfaceName }
            .toSet()

        var i = 0
        while (true) {
            val candidate = "$prefix$i"
            if (candidate !in existing) return candidate
            i++
        }
    }

    private fun getActiveKernelConfig(interfaceName: String) : String? {
        val command = "wg show '$interfaceName' dump"
        val result = Shell.cmd(command).exec()
        return if (result.isSuccess) {
            val rawConfig = result.out.joinToString("\n")
            Timber.d("Root shell stats result: $rawConfig")
            rawConfig
        } else {
            null
        }
    }

    override suspend fun getActiveConfig(id: Int): Result<ActiveConfig?> {
        val handle = runningTunnels[id]
        val activeConfig = handle?.let {
            getActiveConfigInner(it)?.let { activeIpcConfig ->
                ActiveConfig.parseFromIpc(activeIpcConfig)
            }
        }
        return Result.success(activeConfig)
    }

    override suspend fun disableKillSwitch() = runCatching {
        if (!vpnService.isDone) return@runCatching

        context.stopService(Intent(context, VpnService::class.java))

        _status.update { current ->
            current.copy(
                killSwitch = KillSwitchState()
            )
        }
    }

    override suspend fun setBootstrapDnsConfig(config: DnsBoostrapConfig) {
        DnsConfigManager.update(config.protocol, config.upstream)
    }

    override suspend fun start(tunnel: Tunnel, mode: BackendMode): Result<Unit> = runCatching {
        Timber.i("Start request for tunnel: ${tunnel.id}")

        tunnel.updateState(Tunnel.State.Starting)

        val isFirstVpnOrProxyTunnel = _status.value.activeTunnels.values.none {
            it.mode is BackendMode.Vpn || it.mode is BackendMode.Proxy
        }

        var callbackRegistered = false

        try {
            if (isFirstVpnOrProxyTunnel) {
                Timber.i("Kotlin: Registering global status callback (first VPN/Proxy tunnel)")
                VpnBackend.setStatusCallback(statusCallback)
                callbackRegistered = true
            }

            // Update UI state
            _status.update {
                it.copy(
                    activeTunnels = it.activeTunnels + (tunnel.id to ActiveTunnel(
                        Tunnel.State.Starting,
                        mode =mode
                    ))
                )
            }

            val (handle, ifName) = tunnelMutex.withLock {
                if (runningTunnels.containsKey(tunnel.id)) {
                    throw BackendException.StateConflict("Tunnel ${tunnel.id} is already in use")
                }

                val ifName = nextInterfaceName()

                val handle = when (mode) {
                    is BackendMode.Kernel -> {
                        // TODO implement kernel mode
                        99999
                    }
                    is BackendMode.Proxy.KillSwitchPrimary, is BackendMode.Proxy.Standard -> {
                        val bypassNeeded = mode is BackendMode.Proxy.KillSwitchPrimary
                        startProxyTunnel(ifName, mode.config, bypassNeeded)
                    }
                    is BackendMode.Vpn -> {
                        startVpnTunnel(tunnel, ifName, mode.config)
                    }
                }

                if (handle < 0) {
                    throw BackendException.InternalError(
                        "Tunnel failed with internal error code $handle"
                    )
                }
                handle to ifName
            }

            val tunnelStatusJob = backendScope.launch {
                tunnel.features.forEach { feature ->
                    launch {
                        when (feature) {
                            is Tunnel.Feature.ActiveConfigMonitor -> {
                                ActiveConfigFeature().monitor(
                                    tunnelId = tunnel.id,
                                    getRawActiveConfig = { id ->
                                        val runningTunnel = runningTunnels[id] ?: return@monitor null
                                        getActiveConfigInner(runningTunnel)
                                    },
                                    statusUpdater = { id, activeConfig ->
                                        _status.update { backendStatus ->
                                            val current = backendStatus.activeTunnels[id] ?: return@update backendStatus
                                            backendStatus.copy(
                                                activeTunnels = backendStatus.activeTunnels +
                                                        (id to current.copy(activeConfig = activeConfig))
                                            )
                                        }
                                    }
                                )
                            }

                            is Tunnel.Feature.PingMonitor -> {
                                PingFeature().monitor(
                                    tunnelId = tunnel.id,
                                    mode = mode,
                                    feature = feature,
                                    statusUpdater = { id, stats ->
                                        _status.update { backendStatus ->
                                            val current = backendStatus.activeTunnels[id] ?: return@update backendStatus
                                            backendStatus.copy(
                                                activeTunnels = backendStatus.activeTunnels +
                                                        (id to current.copy(pingStats = stats))
                                            )
                                        }
                                    }
                                )
                            }

                            Tunnel.Feature.DynamicDNS -> {
                                // TODO
                            }
                        }
                    }
                }
            }

            if(handle < 0) {
                cleanupTunnel(tunnel)
                throw BackendException.InternalError("Tunnel failed with internal error code $handle")
            }

            runningTunnels[tunnel.id] = RunningTunnel(
                handle = handle,
                interfaceName = ifName,
                mode = mode,
                tunnel = tunnel,
                job = tunnelStatusJob
            )

        } finally {
            // Clean up registered callback if tunnel never started successfully
            if (callbackRegistered && !runningTunnels.containsKey(tunnel.id)) {
                Timber.i("Kotlin: Tunnel failed to start - unregistering global status callback")
                VpnBackend.setStatusCallback(null)
            }
        }
    }

    private fun cleanupTunnel(tunnel: Tunnel) {
        _status.update { current ->
            current.copy(activeTunnels = current.activeTunnels - tunnel.id)
        }
        tunnel.updateState(Tunnel.State.Down)
        val runningTunnel = runningTunnels[tunnel.id] ?: return
        runningTunnel.job?.cancel()
        runningTunnels.remove(tunnel.id)
    }

    private fun startKernelTunnel() {
        // TODO need to handle bypassed DNS via native and job/loop like native currently does for userspace
        // need to also set dummy IP if it is a domain endpoint and then update the config on resolution of peers
        val localTempDir = File(context.cacheDir, "tmp")

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
                    primaryTunnel = null
                )
            )
        }
    }

    override suspend fun stop(id: Int): Result<Unit> = tunnelMutex.withLock {
        val runningTunnel = runningTunnels[id] ?: return Result.failure(BackendException.StateConflict("Tunnel not running"))
        when(val mode = runningTunnel.mode) {
            is BackendMode.Kernel -> {
                // TODO
            }
            is BackendMode.Proxy.KillSwitchPrimary, is BackendMode.Proxy.Standard -> {
                val withSocks5Bridge = mode is BackendMode.Proxy.KillSwitchPrimary
                stopProxyTunnel(runningTunnel, withSocks5Bridge)
            }
            is BackendMode.Vpn -> stopVpnTunnel(runningTunnel)
        }
        cleanupTunnel(runningTunnel.tunnel)
        if (runningTunnels.values.none { it.mode is BackendMode.Proxy || it.mode is BackendMode.Vpn }) {
            VpnBackend.setStatusCallback(null)
        }
        Result.success(Unit)
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

    companion object {
        const val WGT_INTERFACE_PREFIX = "wgtun"
        const val DEFAULT_MTU = 1280
        // for consumer to set AOVPN callback
        var alwaysOnCallback: VpnService.AlwaysOnCallback? = null
        @Volatile
        var vpnService = CompletableFuture<VpnService>()
    }
}