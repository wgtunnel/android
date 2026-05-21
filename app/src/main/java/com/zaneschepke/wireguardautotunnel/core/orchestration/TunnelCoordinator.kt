package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.wireguardautotunnel.core.event.TunnelErrorEvent
import com.zaneschepke.wireguardautotunnel.core.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.data.repository.RoomDnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.events.TunnelActionEvent
import com.zaneschepke.wireguardautotunnel.domain.model.DnsSettings
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.MonitoringSettings
import com.zaneschepke.wireguardautotunnel.domain.model.ProxySettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.ProxySettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TunnelCoordinator(
    private val tunnelProvider: TunnelProvider,
    private val serviceManager: ServiceManager,
    settingsRepository: GeneralSettingRepository,
    private val tunnelRepository: TunnelRepository,
    dnsSettingsRepository: RoomDnsSettingsRepository,
    monitoringSettingsRepository: MonitoringSettingsRepository,
    proxyRepository: ProxySettingsRepository,
    scope: CoroutineScope,
) {

    data class RuntimeSettingsSnapshot(
        val general: GeneralSettings,
        val dns: DnsSettings,
        val monitoring: MonitoringSettings,
        val proxy: ProxySettings,
    )

    private val runtimeSettingsSnapshot =
        combine(
            settingsRepository.flow,
            dnsSettingsRepository.flow,
            monitoringSettingsRepository.flow,
            proxyRepository.flow,
        ) { general, dns, monitoring, proxy ->
            RuntimeSettingsSnapshot(
                general = general,
                dns = dns,
                monitoring = monitoring,
                proxy = proxy,
            )
        }

    private val _actions = MutableSharedFlow<TunnelActionEvent>()
    val actions = _actions.asSharedFlow()

    private val runtimeSettingsSnapshotState =
        runtimeSettingsSnapshot.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    private suspend fun getSnapshot(): RuntimeSettingsSnapshot {
        return runtimeSettingsSnapshotState.filterNotNull().first()
    }

    private var lastActiveTunnels: List<Int> = emptyList()
    private val tunnelMutex = Mutex()
    private val _errors = MutableSharedFlow<TunnelErrorEvent>()
    val errors = _errors.asSharedFlow()

    val backendStatus = tunnelProvider.backendStatus

    suspend fun startTunnel(
        config: TunnelConfig,
        source: TunnelActionSource = TunnelActionSource.USER,
    ) = tunnelMutex.withLock { startTunnelInternal(config, source) }

    suspend fun stopTunnel(id: Int, source: TunnelActionSource = TunnelActionSource.USER) =
        tunnelMutex.withLock {
            stopTunnelInternal(id, source)
        }

    suspend fun stopActiveTunnels() = tunnelMutex.withLock { stopActiveTunnelsInternal() }

    private suspend fun startTunnelInternal(
        tunnelConfig: TunnelConfig,
        source: TunnelActionSource,
    ) {

        val snapshot = getSnapshot()
        val settings = snapshot.general
        val dnsSettings = snapshot.dns
        val proxySettings = snapshot.proxy
        val monitoringSettings = snapshot.monitoring

        val config = tunnelConfig.getConfig()
        val policy =
            ConfigReconciler.ConfigReconcilePolicy(
                dnsSettings.isGlobalTunnelDnsEnabled,
                settings.isGlobalSplitTunnelEnabled,
                settings.isGlobalAmneziaEnabled,
            )

        val runConfig =
            if (policy.hasAnyOverrides) {
                val globalConfig = tunnelRepository.globalTunnelFlow.firstOrNull()?.getConfig()
                ConfigReconciler.reconcileConfig(config, globalConfig, policy)
            } else config

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

                    BackendMode.Proxy.KillSwitchPrimary(runConfig)
                }
            }

        // TODO for now, enforce single tunnel until multi-tunneling is implement
        stopActiveTunnelsInternal()

        tunnelProvider
            .startTunnel(
                tunnel =
                    tunnelConfig.toBackendTunnel(
                        monitoringSettings,
                        settings.tunnelScriptingEnabled,
                    ),
                mode = backendMode,
            )
            .onSuccess {
                _actions.emit(
                    TunnelActionEvent.Started(tunnelId = tunnelConfig.id, source = source)
                )
            }
            .onFailure { _errors.emit(TunnelErrorEvent.from(it, tunnelConfig.id)) }
    }

    suspend fun startDefault() {
        tunnelRepository.getDefaultTunnel()?.let { tunnel -> startTunnel(tunnel) }
    }

    suspend fun toggleTunnels(source: TunnelActionSource = TunnelActionSource.USER) =
        tunnelMutex.withLock {
            val active = tunnelProvider.backendStatus.value.activeTunnels

            if (active.isNotEmpty()) {
                lastActiveTunnels = active.keys.toList()
                stopActiveTunnelsInternal()
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
            .onSuccess { _actions.emit(TunnelActionEvent.Stopped(tunnelId = id, source = source)) }
            .onFailure { _errors.emit(TunnelErrorEvent.from(it, id)) }
    }

    private suspend fun stopActiveTunnelsInternal() {
        tunnelProvider.stopActiveTunnels()
    }
}
