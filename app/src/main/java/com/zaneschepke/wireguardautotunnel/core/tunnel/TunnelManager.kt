package com.zaneschepke.wireguardautotunnel.core.tunnel

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.core.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.domain.events.BackendCoreException
import com.zaneschepke.wireguardautotunnel.domain.events.BackendMessage
import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.LockdownSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.ProxySettingsRepository
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
class TunnelManager(
    private val backend: Backend,
    private val serviceManager: ServiceManager,
    private val settingsRepository: GeneralSettingRepository,
    private val autoTunnelSettingsRepository: AutoTunnelSettingsRepository,
    private val proxyRepository: ProxySettingsRepository,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) : TunnelProvider {

    override val backendStatus: StateFlow<BackendStatus> = backend.status.stateIn(
        scope = applicationScope.plus(ioDispatcher),
        started = SharingStarted.Eagerly,
        initialValue = BackendStatus()
    )

    @OptIn(ExperimentalAtomicApi::class) val currentAppMode = AtomicReference(AppMode.VPN)


    override suspend fun startTunnel(tunnelConfig: TunnelConfig): Result<Unit> {
        val config = tunnelConfig.getConfig()
        val mode = when(currentAppMode.load()) {
            AppMode.VPN -> com.zaneschepke.tunnel.model.BackendMode.Vpn(config)
            AppMode.PROXY -> {
                val proxySettings = proxyRepository.getProxySettings()
                com.zaneschepke.tunnel.model.BackendMode.Proxy.Standard(config, proxySettings.toProxyConfig())
            }
            AppMode.LOCK_DOWN -> com.zaneschepke.tunnel.model.BackendMode.Proxy.KillSwitchPrimary(config)
            AppMode.KERNEL -> com.zaneschepke.tunnel.model.BackendMode.Kernel(config)
        }
        return backend.start(
            object : Tunnel {
                override val id: Int
                    get() = tunnelConfig.id
                override val name: String
                    get() = tunnelConfig.name
                override val isMetered: Boolean
                    get() = tunnelConfig.isMetered

                // TODO
                override val features: Set<Tunnel.Feature>
                    get() = emptySet()

                override fun updateState(state: Tunnel.State) {
                    // nothing to do here
                }

            },
            mode = mode)
    }

    override suspend fun stopTunnel(tunnelId: Int) {
        // TODO handle errors
        backend.stop(tunnelId)
    }

    override suspend fun stopActiveTunnels() {
        //TODO add shutdown function to tunnel
    }
    override suspend fun setLockDown(settings: LockdownSettings) {
        backend.setKillSwitch(settings.toKillSwitchConfig())
    }

    override suspend fun disableLockDown() {
        backend.disableKillSwitch()
    }

    override suspend fun getActiveConfig(tunnelId: Int): ActiveConfig? {
        return backend.getActiveConfig(tunnelId).getOrNull()
    }

    override suspend fun changeAppMode(newMode: AppMode): Result<Unit> {
        stopActiveTunnels()
        when(newMode) {
            AppMode.VPN -> TODO()
            AppMode.PROXY -> TODO()
            AppMode.LOCK_DOWN -> TODO()
            AppMode.KERNEL -> TODO()
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    private val localErrorEvents = MutableSharedFlow<Pair<String?, BackendCoreException>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val localMessageEvents = MutableSharedFlow<Pair<String?, BackendMessage>>()

    override val errorEvents: SharedFlow<Pair<String?, BackendCoreException>> =
        localErrorEvents.shareIn(
                scope = applicationScope + ioDispatcher,
                started = SharingStarted.Eagerly,
                replay = 0,
            )

    override val messageEvents: SharedFlow<Pair<String?, BackendMessage>> =
        localMessageEvents.shareIn(
                scope = applicationScope.plus(ioDispatcher),
                started = SharingStarted.Eagerly,
                replay = 0,
            )

//    private val tunnelServiceHandler =
//        TunnelServiceHandler(
//            activeTunnels = backendStatus,
//            settingsRepository = settingsRepository,
//            serviceManager = serviceManager,
//            applicationScope = applicationScope,
//            ioDispatcher = ioDispatcher,
//        )
//
//    private val tunnelActiveStatePersister =
//        TunnelActiveStatePersister(
//            activeTunnels = backendStatus,
//            tunnelsRepository = tunnelsRepository,
//            applicationScope = applicationScope,
//            ioDispatcher = ioDispatcher,
//        )

//    private val dynamicDnsHandler =
//        DynamicDnsHandler(
//            activeTunnels = activeTunnels,
//            tunnelsRepository = tunnelsRepository,
//            settingsRepository = settingsRepository,
//            localMessageEvents = localMessageEvents,
//            handleDnsReresolve = { config -> handleDnsReresolve(config) },
//            applicationScope = applicationScope,
//            ioDispatcher = ioDispatcher,
//        )
//
//    private val fullTunnelMonitorHandler =
//        TunnelMonitorHandler(
//            activeTunnels = activeTunnels,
//            tunnelsRepository = tunnelsRepository,
//            settingsRepository = settingsRepository,
//            monitoringSettingsRepository = monitoringSettingsRepository,
//            networkMonitor = networkMonitor,
//            networkUtils = networkUtils,
//            powerManager = powerManager,
//            logReader = logReader,
//            getStatistics = { id -> getStatistics(id) },
//            updateTunnelStatus = { id, status, stats, pings, logHealth ->
//                updateTunnelStatus(id, status, stats, pings, logHealth)
//            },
//            applicationScope = applicationScope,
//            ioDispatcher = ioDispatcher,
//        )

    init {
        applicationScope.launch(ioDispatcher) {
            val initialEmit = AtomicBoolean(true)
            settingsRepository.flow
                .filterNotNull()
                .filterNot { it == GeneralSettings() }
                .distinctUntilChangedBy { it.appMode }
                .collect { settings ->
                    val isInitialEmit = initialEmit.exchange(false)
                    val previousMode = currentAppMode.exchange(settings.appMode)

//                    if (isInitialEmit) {
//                        return@collect handleRestore(settings)
//                    }

//                    if (previousMode != settings.appMode) {
//                        handleModeChangeCleanup(previousMode)
//                    }
//                    if (settings.appMode == AppMode.LOCK_DOWN) {
//                        handleLockDownModeInit()
//                    }
                }
        }
    }

    // TODO this can crash if we haven't started foreground service yet, especially for
    // workerManager

//    private suspend fun handleModeChangeCleanup(previousAppMode: AppMode) {
//        lifecycleManagers[previousAppMode]?.stopActiveTunnels()
//        if (previousAppMode == AppMode.LOCK_DOWN) {
//            lifecycleManagers[previousAppMode]?.setBackendMode(BackendMode.Inactive)
//        }
//    }

//    suspend fun handleRestore(settings: GeneralSettings? = null) =
//        withContext(ioDispatcher) {
//            val currentSettings = settings ?: settingsRepository.getGeneralSettings()
//            val autoTunnelSettings = autoTunnelSettingsRepository.getAutoTunnelSettings()
//            val tunnels = tunnelsRepository.userTunnelsFlow.firstOrNull()
//            if (autoTunnelSettings.isAutoTunnelEnabled)
//                return@withContext restoreAutoTunnel(autoTunnelSettings)
//            if (currentSettings.appMode == AppMode.LOCK_DOWN) handleLockDownModeInit()
//            if (tunnels?.any { it.isActive } == true) {
//                if (currentSettings.appMode == AppMode.VPN && !serviceManager.hasVpnPermission())
//                    return@withContext localErrorEvents.emit(null to NotAuthorized())
//                when (currentSettings.appMode) {
//                    AppMode.VPN,
//                    AppMode.PROXY,
//                    AppMode.LOCK_DOWN -> {
//                        tunnels.firstOrNull { it.isActive }?.let { startTunnel(it) }
//                    }
//                    AppMode.KERNEL ->
//                        tunnels.filter { it.isActive }.forEach { conf -> startTunnel(conf) }
//                }
//            }
//        }

    private suspend fun restoreAutoTunnel(autoTunnelSettings: AutoTunnelSettings) {
        autoTunnelSettingsRepository.upsert(autoTunnelSettings.copy(isAutoTunnelEnabled = true))
        serviceManager.startAutoTunnelService()
    }

    suspend fun handleReboot() =
        withContext(ioDispatcher) {
//            val settings = settingsRepository.getGeneralSettings()
//            val autoTunnelSettings = autoTunnelSettingsRepository.getAutoTunnelSettings()
//            val defaultTunnel = tunnelsRepository.getDefaultTunnel()
//            if (autoTunnelSettings.startOnBoot)
//                return@withContext restoreAutoTunnel(autoTunnelSettings)
//            if (settings.isRestoreOnBootEnabled) {
//                tunnelsRepository.resetActiveTunnels()
//                when (settings.appMode) {
//                    AppMode.LOCK_DOWN -> handleLockDownModeInit()
//                    AppMode.VPN ->
//                        if (!serviceManager.hasVpnPermission())
//                            return@withContext localErrorEvents.emit(null to NotAuthorized())
//                    AppMode.KERNEL,
//                    AppMode.PROXY -> Unit
//                }
//                defaultTunnel?.let { startTunnel(it) }
//            }
        }

    suspend fun restartActiveTunnel(id: Int) =
        withContext(ioDispatcher) {
//            val activeIds = backendStatus.value.keys.toList()
//            if (activeIds.isEmpty()) return@withContext
//            if (!activeIds.contains(id)) return@withContext
//            val tunnel = tunnelsRepository.getById(id) ?: return@withContext
//            restartTunnel(tunnel)
        }

    suspend fun restartActiveTunnels() =
        withContext(ioDispatcher) {
//            val activeIds = backendStatus.value.keys.toList()
//            if (activeIds.isEmpty()) return@withContext
//
//            val tunnels = tunnelsRepository.getAll()
//            if (tunnels.isEmpty()) return@withContext
//
//            supervisorScope {
//                activeIds.forEach { id ->
//                    val tunnel =
//                        tunnels.find { it.id == id }
//                            ?: run {
//                                Timber.w("Tunnel config $id not found; skipping restart")
//                                return@forEach
//                            }
//                    restartTunnel(tunnel)
//                }
//            }
        }

    private suspend fun restartTunnel(tunnel: TunnelConfig) {
        runCatching { stopTunnel(tunnel.id) }
            .onFailure { e -> Timber.e(e, "Failed to stop tunnel ${tunnel.id} during restart") }

        delay(RESTART_TUNNEL_DELAY)

        runCatching { startTunnel(tunnel) }
            .onFailure { e -> Timber.e(e, "Failed to restart tunnel ${tunnel.id}") }
    }

    companion object {
        const val RESTART_TUNNEL_DELAY = 300L
    }
}
