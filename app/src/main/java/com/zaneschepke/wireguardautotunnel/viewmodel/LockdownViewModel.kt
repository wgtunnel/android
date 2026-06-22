package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.LockdownSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.LockdownSettingsUiState
import com.zaneschepke.wireguardautotunnel.util.StringValue
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class LockdownViewModel(
    private val lockdownSettingsRepository: LockdownSettingsRepository,
    private val tunnelProvider: TunnelProvider,
    private val globalEffectRepository: GlobalEffectRepository,
) : ContainerHost<LockdownSettingsUiState, Nothing>, ViewModel() {

    override val container =
        container<LockdownSettingsUiState, Nothing>(
            LockdownSettingsUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            lockdownSettingsRepository.flow.collect {
                reduce {
                    state.copy(
                        lockdownSettings = it,
                        metered = it.metered,
                        dualStack = it.dualStack,
                        bypassLan = it.bypassLan,
                        isLoading = false,
                    )
                }
            }
        }

    fun setLockdownSettings() = intent {
        reduce { state.copy(showSaveModal = false) }

        val updated =
            state.lockdownSettings.copy(
                metered = state.metered,
                dualStack = state.dualStack,
                bypassLan = state.bypassLan,
            )

        lockdownSettingsRepository.upsert(updated)

        tunnelProvider.disableLockDown()
        tunnelProvider.setLockDown(updated)

        postSideEffect(GlobalSideEffect.PopBackStack)
        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.config_changes_saved),
                ToastType.Success,
            )
        )
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun setShowSaveModal(to: Boolean) = intent { reduce { state.copy(showSaveModal = to) } }

    fun setMetered(value: Boolean) = intent { reduce { state.copy(metered = value) } }

    fun setDualStack(value: Boolean) = intent { reduce { state.copy(dualStack = value) } }

    fun setBypassLan(value: Boolean) = intent { reduce { state.copy(bypassLan = value) } }
}
