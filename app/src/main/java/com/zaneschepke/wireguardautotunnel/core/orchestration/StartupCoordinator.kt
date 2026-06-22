package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import kotlinx.coroutines.flow.first

class StartupCoordinator(
    private val tunnelCoordinator: TunnelCoordinator,
    private val settingsRepository: GeneralSettingRepository,
    private val autoTunnelCoordinator: AutoTunnelCoordinator,
    private val tunnelRepository: TunnelRepository,
    private val bootstrapCoordinator: AppBoostrapCoordinator,
) {

    suspend fun applyStartupPolicy(): Result<Unit> = runCatching {
        val shouldRestoreAutoTunnel = autoTunnelCoordinator.shouldRestore()
        val settings = settingsRepository.getGeneralSettings()
        val shouldRestoreDefaultTunnel = settings.isRestoreOnBootEnabled

        if (shouldRestoreAutoTunnel || shouldRestoreDefaultTunnel) {
            // Wait for app critical bootstrap to finish
            bootstrapCoordinator.isReady.first { it }
        } else {
            return Result.success(Unit)
        }

        if (shouldRestoreAutoTunnel) {
            autoTunnelCoordinator.start()
            return Result.success(Unit)
        }

        val defaultTunnel = tunnelRepository.getDefaultTunnel() ?: return Result.success(Unit)
        tunnelCoordinator.startTunnel(defaultTunnel)
        return Result.success(Unit)
    }
}
