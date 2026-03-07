package com.zaneschepke.wireguardautotunnel.core.tunnel.handler

import com.zaneschepke.wireguardautotunnel.core.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber

class TunnelServiceHandler(
    private val activeTunnels: StateFlow<Map<Int, TunnelState>>,
    private val settingsRepository: GeneralSettingRepository,
    private val serviceManager: ServiceManager,
    applicationScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
) {
    init {
        applicationScope.launch(ioDispatcher) {
            activeTunnels.collect { activeTuns ->
                if (activeTuns.isEmpty()) {
                    Timber.d("Stopping tunnel service, no tunnels active.")
                    serviceManager.stopTunnelService()
                } else if (serviceManager.tunnelService.value == null) {
                    val settings = settingsRepository.flow.firstOrNull() ?: GeneralSettings()
                    Timber.d("Starting tunnel foreground service for active tunnel.")
                    serviceManager.startTunnelService(settings.appMode)
                }
                serviceManager.updateTunnelTile()
            }
        }
    }
}
