package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.dokar.sonner.ToastType
import com.wgtunnel.parser.ConfigParseException
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.enums.MimicMode
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.EditableConfig
import com.zaneschepke.wireguardautotunnel.ui.state.EditableInterface
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.asStringValue
import kotlinx.coroutines.flow.combine
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

data class GlobalAmneziaUiState(
    val isLoading: Boolean = true,
    val tunnelId: Int? = null,
    val interfaceDraft: EditableInterface = EditableInterface(),
    val enabled: Boolean = false,
    val tunnelActive: Boolean = false,
)

class GlobalAmneziaViewModel(
    private val tunnelRepository: TunnelRepository,
    private val settingsRepository: GeneralSettingRepository,
    private val globalEffectRepository: GlobalEffectRepository,
    private val tunnelCoordinator: TunnelCoordinator,
) : OrbitContainerHost<GlobalAmneziaUiState, GlobalAmneziaUiState, Nothing>, ViewModel() {

    override val container =
        orbitContainer<GlobalAmneziaUiState, Nothing>(
            GlobalAmneziaUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            combine(
                    tunnelRepository.globalTunnelFlow,
                    settingsRepository.flow,
                    tunnelCoordinator.backendStatus,
                ) { globalTunnel, settings, backendStatus ->
                    Triple(globalTunnel, settings.isGlobalAmneziaEnabled, backendStatus)
                }
                .collect { (globalTunnel, enabled, backendStatus) ->
                    intent {
                        val active = backendStatus.activeTunnels.isNotEmpty()
                        // Room can emit null before the bootstrap global config exists.
                        // Seed once that row is available, and never overwrite a draft the user is
                        // editing.
                        val needsDraft =
                            state.isLoading || (state.tunnelId == null && globalTunnel != null)
                        if (needsDraft) {
                            val draft =
                                globalTunnel?.let {
                                    EditableInterface.from(it.getConfig().`interface`)
                                } ?: EditableInterface()
                            reduce {
                                state.copy(
                                    isLoading = false,
                                    tunnelId = globalTunnel?.id,
                                    interfaceDraft = draft,
                                    enabled = enabled,
                                    tunnelActive = active,
                                )
                            }
                        } else {
                            reduce { state.copy(enabled = enabled, tunnelActive = active) }
                        }
                    }
                }
        }

    fun onInterfaceChange(editableInterface: EditableInterface) = intent {
        reduce { state.copy(interfaceDraft = editableInterface) }
    }

    fun onMimic(mimic: MimicMode) = intent {
        val updated =
            when (mimic) {
                MimicMode.QUIC -> state.interfaceDraft.setQuicMimic()
                MimicMode.DNS -> state.interfaceDraft.setDnsMimic()
                MimicMode.SIP -> state.interfaceDraft.setSipMimic()
            }
        reduce { state.copy(interfaceDraft = updated) }
    }

    fun toggleAmneziaCompat() = intent {
        val updated =
            if (state.interfaceDraft.isAmneziaCompatibilityModeSet()) {
                state.interfaceDraft.resetAmneziaProperties()
            } else {
                state.interfaceDraft.applyAmneziaDefaults()
            }
        reduce { state.copy(interfaceDraft = updated) }
    }

    fun save(restart: Boolean = false) = intent {
        val stored = state.tunnelId?.let { tunnelRepository.getById(it) } ?: return@intent
        val merged =
            EditableConfig.from(stored.getConfig()).let {
                it.copy(`interface` = it.`interface`.withAmneziaFrom(state.interfaceDraft))
            }
        val config =
            try {
                merged.validatedConfig(stored.name)
            } catch (e: ConfigParseException) {
                postSideEffect(GlobalSideEffect.Snackbar(e.asStringValue(), ToastType.Error))
                return@intent
            }
        tunnelRepository.save(
            stored.copy(quickConfig = config.withName(stored.name).asQuickString()),
        )
        if (restart) tunnelCoordinator.restartActiveTunnels()
        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.config_changes_saved),
                ToastType.Success,
            ),
        )
        postSideEffect(GlobalSideEffect.PopBackStack)
    }

    private suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }
}
