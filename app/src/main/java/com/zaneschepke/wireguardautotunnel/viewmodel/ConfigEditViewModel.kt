package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.parser.ConfigParseException
import com.zaneschepke.wireguardautotunnel.ui.state.ConfigProxy
import com.zaneschepke.wireguardautotunnel.ui.state.ConfigUiState
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.asStringValue
import kotlinx.coroutines.flow.combine
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber

class ConfigEditViewModel(
    private val tunnelRepository: TunnelRepository,
    private val globalEffectRepository: GlobalEffectRepository,
    private val tunnelProvider: TunnelProvider,
    val tunnelId: Int?,
) : ContainerHost<ConfigUiState, Nothing>, ViewModel() {

    override val container =
        container<ConfigUiState, Nothing>(
            ConfigUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            combine(tunnelProvider.backendStatus, tunnelRepository.flow) { backendStatus, tuns ->
                    val tunnel = tuns.firstOrNull { it.id == tunnelId }
                    val tunnelNames = tuns.filter { it.id != tunnelId }.map { it.name }
                    val isRunning = backendStatus.activeTunnels.containsKey(tunnelId)
                    state.copy(
                        unavailableNames = tunnelNames,
                        isLoading = false,
                        tunnel = tunnel,
                        isRunning = isRunning,
                    )
                }
                .collect { state -> reduce { state } }
        }

    fun saveConfigProxy(configProxy: ConfigProxy, tunnelName: String) = intent {
        reduce { state.copy(showSaveModal = false) }
        if (state.unavailableNames.contains(tunnelName))
            return@intent postSideEffect(
                GlobalSideEffect.Toast(StringValue.StringResource(R.string.tunnel_name_taken))
            )
        runCatching {
                val config = configProxy.buildConfig()
                val quickConfig = config.asQuickString()
                val tunnelConfig =
                    if (tunnelId == null) {
                        TunnelConfig.tunnelConfFromQuick(quickConfig, tunnelName)
                    } else {
                        state.tunnel?.copy(name = tunnelName, quickConfig = quickConfig)
                    }
                if (tunnelConfig != null) {
                    tunnelRepository.save(tunnelConfig)

                    // TODO
                    //                    if (state.isRunning)
                    // tunnelBackendProvider.restartActiveTunnel(tunnelConfig.id)

                    postSideEffect(
                        GlobalSideEffect.Toast(
                            StringValue.StringResource(R.string.config_changes_saved)
                        )
                    )
                    postSideEffect(GlobalSideEffect.PopBackStack)
                }
            }
            .onFailure {
                Timber.e(it)
                val message =
                    when (it) {
                        is ConfigParseException -> it.asStringValue()
                        else -> StringValue.StringResource(R.string.unknown_error)
                    }
                postSideEffect(GlobalSideEffect.Snackbar(message))
            }
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun setShowSaveModal(showSaveModal: Boolean) = intent {
        reduce { state.copy(showSaveModal = showSaveModal) }
    }
}
