package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.util.BackendException
import com.zaneschepke.wireguardautotunnel.core.event.TunnelErrorEvent
import com.zaneschepke.wireguardautotunnel.core.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.data.model.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.ProxySettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TunnelCoordinator(
    private val tunnelProvider: TunnelProvider,
    private val serviceManager: ServiceManager,
    private val settingsRepository: GeneralSettingRepository,
    private val tunnelRepository: TunnelRepository,
    private val proxyRepository: ProxySettingsRepository,
) {

    private var lastActiveTunnels: List<Int> = emptyList()
    private val tunnelMutex = Mutex()
    private val _errors = MutableSharedFlow<TunnelErrorEvent>()
    val errors = _errors.asSharedFlow()

    val backendStatus = tunnelProvider.backendStatus

    suspend fun startTunnel(config: TunnelConfig) =
        tunnelMutex.withLock {
            startTunnelInternal(config)
        }

    suspend fun stopTunnel(id: Int) =
        tunnelMutex.withLock {
            stopTunnelInternal(id)
        }

    suspend fun stopActiveTunnels() =
        tunnelMutex.withLock {
            stopActiveTunnelsInternal()
        }
    private suspend fun startTunnelInternal(config: TunnelConfig) {

        val settings = settingsRepository.getGeneralSettings()

        val backendMode =
            when (settings.tunnelMode) {
                TunnelMode.VPN -> {

                    if (!serviceManager.hasVpnPermission()) {
                        _errors.emit(TunnelErrorEvent.VpnPermissionDenied(config.id))
                        return
                    }

                    BackendMode.Vpn(config.getConfig())
                }

                TunnelMode.PROXY -> {

                    val proxySettings = proxyRepository.getProxySettings()

                    BackendMode.Proxy.Standard(
                        config = config.getConfig(),
                        proxyConfig = proxySettings.toProxyConfig(),
                    )
                }

                TunnelMode.LOCK_DOWN -> {

                    BackendMode.Proxy.KillSwitchPrimary(config.getConfig())
                }
            }

        tunnelProvider
            .startTunnel(tunnel = config.toBackendTunnel(), mode = backendMode)
            .onFailure { _errors.emit(TunnelErrorEvent.from(it,config.id)) }
    }

    suspend fun startDefault() {
        tunnelRepository.getDefaultTunnel()?.let { tunnel ->
            startTunnel(tunnel)
        }
    }

    suspend fun toggleTunnels() =
        tunnelMutex.withLock {

            val active =
                tunnelProvider.backendStatus.value.activeTunnels

            if (active.isNotEmpty()) {
                lastActiveTunnels = active.keys.toList()
                stopActiveTunnelsInternal()
                return@withLock
            }

            val tunnelsToStart =
                when {
                    lastActiveTunnels.isNotEmpty() -> {
                        lastActiveTunnels.mapNotNull {
                            tunnelRepository.getById(it)
                        }
                    }

                    else -> {
                        tunnelRepository.getDefaultTunnel()?.let(::listOf)
                            ?: emptyList()
                    }
                }

            tunnelsToStart.forEach {
                startTunnelInternal(it)
            }
        }

    private suspend fun stopTunnelInternal(id: Int) {
        tunnelProvider.stopTunnel(id).onFailure { _errors.emit(TunnelErrorEvent.from(it, id)) }
    }

    private suspend fun stopActiveTunnelsInternal() {
        tunnelProvider.stopActiveTunnels()
    }
}
