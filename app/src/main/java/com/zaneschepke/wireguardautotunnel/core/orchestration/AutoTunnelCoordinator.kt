package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.wireguardautotunnel.core.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.core.service.autotunnel.AutoTunnelStateHolder
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository

class AutoTunnelCoordinator(
    private val repository: AutoTunnelSettingsRepository,
    private val serviceManager: ServiceManager,
    private val autoTunnelStateHolder: AutoTunnelStateHolder,
) {

    suspend fun shouldTakeOverBoot(): Boolean {
        val settings = repository.getAutoTunnelSettings()
        return settings.startOnBoot && settings.isAutoTunnelEnabled
    }

    suspend fun restoreIfNeeded(): Boolean {
        if (!shouldTakeOverBoot()) return false

        serviceManager.startAutoTunnelService()
        return true
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
