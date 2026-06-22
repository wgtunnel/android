package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.service.autotunnel.AutoTunnelStateHolder

class AutoTunnelCoordinator(
    private val repository: AutoTunnelSettingsRepository,
    private val serviceManager: ServiceManager,
    private val autoTunnelStateHolder: AutoTunnelStateHolder,
) {

    suspend fun shouldRestore(): Boolean {
        val settings = repository.getAutoTunnelSettings()
        return settings.startOnBoot && settings.isAutoTunnelEnabled
    }

    fun start() {
        serviceManager.startAutoTunnelService()
    }

    suspend fun enable() {
        repository.updateAutoTunnelEnabled(true)
        serviceManager.startAutoTunnelService()
    }

    suspend fun toggle() {
        val running = autoTunnelStateHolder.active.value
        if (running) {
            disable()
        } else enable()
    }

    suspend fun disable() {
        repository.updateAutoTunnelEnabled(false)
        serviceManager.stopAutoTunnelService()
    }
}
