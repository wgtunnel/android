package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.LockdownSettingsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class TunnelBackendCoordinator(
    private val tunnelCoordinator: TunnelCoordinator,
    private val tunnelProvider: TunnelProvider,
    private val settingsRepository: GeneralSettingRepository,
    private val lockdownRepository: LockdownSettingsRepository,
) {

    suspend fun changeMode(newMode: TunnelMode): Result<Unit> {

        val settings = settingsRepository.getGeneralSettings()
        val oldMode = settings.tunnelMode

        if (oldMode == newMode) {
            return Result.success(Unit)
        }

        // Holds the tunnel lock so a lockdown save or tunnel start can't rebuild the kill switch
        // in between, and is not cancellable so a half finished switch can't be left behind
        return withContext(NonCancellable) {
            tunnelCoordinator.exclusively {
                runCatching {
                    tunnelProvider.stopActiveTunnels().getOrThrow()
                    exitMode(oldMode)
                    enterMode(newMode)

                    settingsRepository.upsert(settings.copy(tunnelMode = newMode))
                }
            }
        }
    }

    private suspend fun exitMode(oldMode: TunnelMode) {
        when (oldMode) {
            TunnelMode.LOCK_DOWN -> {
                tunnelProvider.disableLockDown().getOrThrow()
            }
            else -> Unit
        }
    }

    private suspend fun enterMode(newMode: TunnelMode) {
        when (newMode) {
            TunnelMode.LOCK_DOWN -> {
                val lockdownSettings = lockdownRepository.getLockdownSettings()
                tunnelProvider.setLockDown(lockdownSettings).getOrThrow()
            }

            TunnelMode.VPN,
            TunnelMode.PROXY -> Unit
        }
    }
}
