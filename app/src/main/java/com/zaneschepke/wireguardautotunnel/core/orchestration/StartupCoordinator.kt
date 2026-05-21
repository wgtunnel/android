package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.wireguardautotunnel.core.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.LockdownSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository

class StartupCoordinator(
    private val tunnelCoordinator: TunnelCoordinator,
    private val tunnelProvider: TunnelProvider,
    private val settingsRepository: GeneralSettingRepository,
    private val autoTunnelCoordinator: AutoTunnelCoordinator,
    private val tunnelRepository: TunnelRepository,
    private val lockdownRepository: LockdownSettingsRepository,
    private val serviceManager: ServiceManager,
) {

    suspend fun applyStartupPolicy(): Result<Unit> {

        val settings = settingsRepository.getGeneralSettings()

        if (!settings.isRestoreOnBootEnabled) {
            return Result.success(Unit)
        }

        val autoTunnelTookOver = autoTunnelCoordinator.restoreIfNeeded()

        if (autoTunnelTookOver) {
            return Result.success(Unit)
        }

        val mode = settings.tunnelMode

        if (mode == TunnelMode.VPN && !serviceManager.hasVpnPermission()) {
            return Result.failure(IllegalStateException("VPN permission missing"))
        }

        if (mode == TunnelMode.LOCK_DOWN) {
            val lockdownSettings = lockdownRepository.getLockdownSettings()
            tunnelProvider.setLockDown(lockdownSettings).getOrElse {
                return Result.failure(it)
            }
        }

        val defaultTunnel = tunnelRepository.getDefaultTunnel() ?: return Result.success(Unit)

        tunnelCoordinator.startTunnel(defaultTunnel)

        return Result.success(Unit)
    }
}
