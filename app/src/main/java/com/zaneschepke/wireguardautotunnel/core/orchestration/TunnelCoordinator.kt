package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.dokar.sonner.ToastType
import com.wgtunnel.backend.model.BackendMode
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.event.TunnelErrorEvent
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.data.repository.RoomDnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.events.TunnelActionEvent
import com.zaneschepke.wireguardautotunnel.domain.model.DnsSettings
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.LockdownSettings
import com.zaneschepke.wireguardautotunnel.domain.model.MonitoringSettings
import com.zaneschepke.wireguardautotunnel.domain.model.ProxySettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.AppStateRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.LockdownSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.ProxySettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.toTunnelDnsConfigOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class TunnelCoordinator(
    private val tunnelProvider: TunnelProvider,
    private val serviceManager: ServiceManager,
    private val bootstrapCoordinator: AppBoostrapCoordinator,
    settingsRepository: GeneralSettingRepository,
    private val tunnelRepository: TunnelRepository,
    dnsSettingsRepository: RoomDnsSettingsRepository,
    monitoringSettingsRepository: MonitoringSettingsRepository,
    globalEffectRepository: GlobalEffectRepository,
    proxyRepository: ProxySettingsRepository,
    lockdownModeRepository: LockdownSettingsRepository,
    private val appStateRepository: AppStateRepository,
    scope: CoroutineScope,
) {

    private val _userOverrideFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val userOverrideFlow = _userOverrideFlow.asSharedFlow()

    data class RuntimeSettingsSnapshot(
        val general: GeneralSettings,
        val dns: DnsSettings,
        val monitoring: MonitoringSettings,
        val proxy: ProxySettings,
        val lockdown: LockdownSettings,
    )

    private val runtimeSettingsSnapshot =
        combine(
            settingsRepository.flow,
            dnsSettingsRepository.flow,
            monitoringSettingsRepository.flow,
            proxyRepository.flow,
            lockdownModeRepository.flow,
        ) { general, dns, monitoring, proxy, lockdown ->
            RuntimeSettingsSnapshot(
                general = general,
                dns = dns,
                monitoring = monitoring,
                proxy = proxy,
                lockdown = lockdown,
            )
        }

    private val _actions = MutableSharedFlow<TunnelActionEvent>(extraBufferCapacity = 8)
    val actions = _actions.asSharedFlow()

    private val runtimeSettingsSnapshotState =
        runtimeSettingsSnapshot.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    val backendStatus = tunnelProvider.backendStatus

    init {
        scope.launch {
            combine(
                    runtimeSettingsSnapshot,
                    tunnelRepository.userTunnelsFlow,
                    backendStatus,
                ) { snapshot, tunnels, status ->
                    val activeIds = status.activeTunnels.keys
                    LiveTunnelFeatureKey(
                        statsEnabled = snapshot.monitoring.tunnelStatisticsEnabled,
                        statsInterval = snapshot.monitoring.tunnelStatisticsPollInterval,
                        seamlessRecovery = snapshot.general.seamlessRecoveryEnabled,
                        bounceDelaySec = snapshot.general.seamlessRecoveryBounceDelaySec,
                        perTunnel =
                            tunnels.associate { tun ->
                                tun.id to
                                    Triple(
                                        tun.isDDNSTunnel,
                                        tun.isIpv6Preferred,
                                        tun.ipv6RestoreEnabled,
                                    )
                            },
                    ) to
                        LiveTunnelFeaturePayload(
                            general = snapshot.general,
                            monitoring = snapshot.monitoring,
                            tunnels = tunnels.filter { it.id in activeIds },
                        )
                }
                .distinctUntilChangedBy { it.first }
                .drop(1)
                .collect { (_, payload) ->
                    payload.tunnels.forEach { config ->
                        tunnelProvider
                            .updateTunnel(
                                config.toBackendTunnel(
                                    payload.monitoring,
                                    payload.general.tunnelScriptingEnabled,
                                    payload.general,
                                )
                            )
                            .onFailure {
                                Timber.e(
                                    it,
                                    "Failed to apply live tunnel features to tunnel ${config.id}",
                                )
                            }
                            .onSuccess {
                                globalEffectRepository.post(
                                    GlobalSideEffect.Snackbar(
                                        message =
                                            StringValue.StringResource(
                                                R.string.active_tunnel_updated
                                            ),
                                        ToastType.Success,
                                    )
                                )
                            }
                    }
                }
        }
    }

    private data class LiveTunnelFeatureKey(
        val statsEnabled: Boolean,
        val statsInterval: Int,
        val seamlessRecovery: Boolean,
        val bounceDelaySec: Int,
        val perTunnel: Map<Int, Triple<Boolean, Boolean, Boolean>>,
    )

    private data class LiveTunnelFeaturePayload(
        val general: GeneralSettings,
        val monitoring: MonitoringSettings,
        val tunnels: List<TunnelConfig>,
    )

    private suspend fun getSnapshot(): RuntimeSettingsSnapshot {
        return runtimeSettingsSnapshotState.filterNotNull().first()
    }

    private var lastActiveTunnels: List<Int> = emptyList()
    private val tunnelMutex = Mutex()
    private val _errors = MutableSharedFlow<TunnelErrorEvent>()
    val errors = _errors.asSharedFlow()

    suspend fun startTunnel(
        config: TunnelConfig,
        source: TunnelActionSource = TunnelActionSource.USER,
    ) = tunnelMutex.withLock {
        // wait for app to be bootstrapped
        bootstrapCoordinator.isReady.first { it }

        if (source == TunnelActionSource.USER) {
            _userOverrideFlow.tryEmit(Unit)
        }

        // enforce single tunnel, for now — do not clear last-active here; start success replaces it
        if (backendStatus.value.activeTunnels.isNotEmpty()) {
            stopActiveTunnelsInternal(source, persistLastActive = false)
        }

        startTunnelInternal(config, source)
    }

    suspend fun stopTunnel(id: Int, source: TunnelActionSource = TunnelActionSource.USER) =
        tunnelMutex.withLock {
            if (source == TunnelActionSource.USER) {
                _userOverrideFlow.tryEmit(Unit)
            }
            stopTunnelInternal(id, source)
        }

    suspend fun stopActiveTunnels(source: TunnelActionSource = TunnelActionSource.USER) =
        tunnelMutex.withLock {
            if (source == TunnelActionSource.USER) {
                _userOverrideFlow.tryEmit(Unit)
            }
            stopActiveTunnelsInternal(source, persistLastActive = true)
        }

    private suspend fun startTunnelInternal(
        tunnelConfig: TunnelConfig,
        source: TunnelActionSource,
    ) {

        val snapshot = getSnapshot()
        val settings = snapshot.general
        val dnsSettings = snapshot.dns
        val proxySettings = snapshot.proxy
        val monitoringSettings = snapshot.monitoring
        val lockdownSettings = snapshot.lockdown

        var config = tunnelConfig.getConfig()

        // makes sure Amnezia configs are 2.0 compatible
        config = AmneziaConfigNormalizer.ensureAmneziaCompatibility(config)

        val policy =
            ConfigReconciler.ConfigReconcilePolicy(
                dnsSettings.isGlobalTunnelConfigDnsEnabled,
                settings.isGlobalSplitTunnelEnabled,
                settings.isGlobalAmneziaEnabled,
            )

        val runConfig =
            if (policy.hasAnyOverrides) {
                val globalConfig = tunnelRepository.globalTunnelFlow.firstOrNull()?.getConfig()
                ConfigReconciler.reconcileConfig(config, globalConfig, policy)
            } else config

        val tunnelDnsConfig = dnsSettings.toTunnelDnsConfigOrNull(runConfig)

        val backendMode =
            when (settings.tunnelMode) {
                TunnelMode.VPN -> {

                    if (!serviceManager.hasVpnPermission()) {
                        _errors.emit(TunnelErrorEvent.VpnPermissionDenied(tunnelConfig.id))
                        return
                    }

                    BackendMode.Vpn(runConfig)
                }

                TunnelMode.PROXY -> {
                    BackendMode.Proxy.Standard(
                        config = runConfig,
                        proxyConfig = proxySettings.toProxyConfig(),
                    )
                }

                TunnelMode.LOCK_DOWN -> {
                    BackendMode.Proxy.KillSwitchPrimary(
                        runConfig,
                        lockdownSettings.toKillSwitchConfig(),
                    )
                }
            }

        tunnelProvider
            .startTunnel(
                tunnel =
                    tunnelConfig.toBackendTunnel(
                        monitoringSettings,
                        settings.tunnelScriptingEnabled,
                        settings,
                    ),
                mode = backendMode,
                tunnelDnsConfig,
            )
            .onSuccess {
                _actions.emit(
                    TunnelActionEvent.Started(tunnelId = tunnelConfig.id, source = source)
                )
                replaceLastActiveTunnelIds(
                    tunnelProvider.backendStatus.value.activeTunnels.keys + tunnelConfig.id
                )
            }
            .onFailure {
                Timber.e(it)
                _errors.emit(TunnelErrorEvent.from(it, tunnelConfig.id))
            }
    }

    suspend fun startDefault() {
        tunnelRepository.getDefaultTunnel()?.let { tunnel -> startTunnel(tunnel) }
    }

    suspend fun restartActiveTunnels() = tunnelMutex.withLock {
        val configs =
            backendStatus.value.activeTunnels.keys.mapNotNull { tunnelRepository.getById(it) }
        if (configs.isEmpty()) return@withLock
        stopActiveTunnelsInternal(TunnelActionSource.USER, persistLastActive = true)
        configs.forEach { startTunnelInternal(it, TunnelActionSource.USER) }
    }

    /**
     * Rebuild the kill-switch TUN in place. HEV is rebound to the new fd by VpnService using the
     * still-running SOCKS listener
     */
    suspend fun applyLockdownSettings(settings: LockdownSettings) = tunnelMutex.withLock {
        tunnelProvider
            .setLockDown(settings)
            .onFailure { Timber.e(it, "Failed to apply lockdown/kill-switch settings") }
            .getOrThrow()
    }

    suspend fun toggleTunnel(
        tunnelConfig: TunnelConfig,
        source: TunnelActionSource = TunnelActionSource.USER,
    ) = tunnelMutex.withLock {
        if (source == TunnelActionSource.USER) {
            _userOverrideFlow.tryEmit(Unit)
        }

        val isActive =
            tunnelProvider.backendStatus.value.activeTunnels.keys.contains(tunnelConfig.id)
        if (isActive) {
            stopTunnelInternal(tunnelConfig.id, source)
            return@withLock
        }
        startTunnelInternal(tunnelConfig, source)
    }

    // for quick settings tile
    suspend fun toggleActiveTunnels(source: TunnelActionSource = TunnelActionSource.USER) =
        tunnelMutex.withLock {
            if (source == TunnelActionSource.USER) {
                _userOverrideFlow.tryEmit(Unit)
            }

            val active = tunnelProvider.backendStatus.value.activeTunnels
            if (active.isNotEmpty()) {
                lastActiveTunnels = active.keys.toList()

                active.keys.forEach { id ->
                    _actions.emit(TunnelActionEvent.Stopped(tunnelId = id, source = source))
                }

                stopActiveTunnelsInternal(source, persistLastActive = true)
                return@withLock
            }

            val tunnelsToStart =
                when {
                    lastActiveTunnels.isNotEmpty() -> {
                        lastActiveTunnels.mapNotNull { tunnelRepository.getById(it) }
                    }

                    else -> {
                        tunnelRepository.getDefaultTunnel()?.let(::listOf) ?: emptyList()
                    }
                }

            tunnelsToStart.forEach { startTunnelInternal(it, source) }
        }

    private suspend fun stopTunnelInternal(id: Int, source: TunnelActionSource) {
        tunnelProvider
            .stopTunnel(id)
            .onSuccess {
                _actions.emit(TunnelActionEvent.Stopped(tunnelId = id, source = source))
                replaceLastActiveTunnelIds(
                    tunnelProvider.backendStatus.value.activeTunnels.keys - id
                )
            }
            .onFailure { _errors.emit(TunnelErrorEvent.from(it, id)) }
    }

    private suspend fun stopActiveTunnelsInternal(
        source: TunnelActionSource = TunnelActionSource.USER,
        persistLastActive: Boolean,
    ) {
        val active = tunnelProvider.backendStatus.value.activeTunnels

        active.keys.forEach { id ->
            _actions.emit(TunnelActionEvent.Stopped(tunnelId = id, source = source))
        }

        tunnelProvider.stopActiveTunnels()
        if (persistLastActive) {
            replaceLastActiveTunnelIds(emptySet())
        }
    }

    private suspend fun replaceLastActiveTunnelIds(ids: Set<Int>) {
        if (ids.isEmpty()) {
            appStateRepository.clearLastActiveTunnelIds()
        } else {
            appStateRepository.setLastActiveTunnelIds(ids.toList())
        }
    }
}
