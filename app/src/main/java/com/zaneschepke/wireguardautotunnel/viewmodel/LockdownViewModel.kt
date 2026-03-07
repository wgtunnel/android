package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.domain.enums.BackendMode
import com.zaneschepke.wireguardautotunnel.domain.model.LockdownSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.LockdownSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.LockdownSettingsUiState
import com.zaneschepke.wireguardautotunnel.util.StringValue
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class LockdownViewModel(
    private val lockdownSettingsRepository: LockdownSettingsRepository,
    private val tunnelManager: TunnelManager,
    private val globalEffectRepository: GlobalEffectRepository,
) : ContainerHost<LockdownSettingsUiState, Nothing>, ViewModel() {

    override val container =
        container<LockdownSettingsUiState, Nothing>(
            LockdownSettingsUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            lockdownSettingsRepository.flow.collect {
                reduce { state.copy(lockdownSettings = it, isLoading = false) }
            }
        }

    fun setLockdownSettings(lockdownSettings: LockdownSettings) = intent {
        reduce { state.copy(showSaveModal = false) }
        lockdownSettingsRepository.upsert(lockdownSettings)

        tunnelManager.setBackendMode(BackendMode.Inactive)
        val allowedIps =
            if (lockdownSettings.bypassLan) TunnelConfig.LAN_BYPASS_ALLOWED_IPS else emptySet()
        tunnelManager.setBackendMode(
            BackendMode.KillSwitch(
                allowedIps = allowedIps,
                isMetered = lockdownSettings.metered,
                dualStack = lockdownSettings.dualStack,
            )
        )

        postSideEffect(GlobalSideEffect.PopBackStack)
        postSideEffect(
            GlobalSideEffect.Toast(StringValue.StringResource(R.string.config_changes_saved))
        )
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun setShowSaveModal(to: Boolean) = intent { reduce { state.copy(showSaveModal = to) } }
}
