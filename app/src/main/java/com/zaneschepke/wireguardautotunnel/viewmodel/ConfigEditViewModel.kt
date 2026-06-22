package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.enums.MimicMode
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.DnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.parser.ConfigParseException
import com.zaneschepke.wireguardautotunnel.ui.state.ConfigDraft
import com.zaneschepke.wireguardautotunnel.ui.state.ConfigEditUiState
import com.zaneschepke.wireguardautotunnel.ui.state.ConfigUiState
import com.zaneschepke.wireguardautotunnel.ui.state.EditableConfig
import com.zaneschepke.wireguardautotunnel.ui.state.EditableInterface
import com.zaneschepke.wireguardautotunnel.ui.state.EditablePeer
import com.zaneschepke.wireguardautotunnel.ui.state.GlobalSettingsState
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.asStringValue
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber

class ConfigEditViewModel(
    private val tunnelRepository: TunnelRepository,
    private val dnsSettingsRepository: DnsSettingsRepository,
    private val settingsRepository: GeneralSettingRepository,
    private val globalEffectRepository: GlobalEffectRepository,
    private val tunnelCoordinator: TunnelCoordinator,
    val tunnelId: Int?,
) : ContainerHost<ConfigUiState, Nothing>, ViewModel() {

    override val container =
        container<ConfigUiState, Nothing>(
            ConfigUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            combine(
                    tunnelCoordinator.backendStatus,
                    tunnelRepository.flow,
                    dnsSettingsRepository.flow.map { it.isGlobalTunnelDnsEnabled },
                    settingsRepository.flow.map { it.isGlobalAmneziaEnabled },
                ) { backendStatus, tunnels, globalDnsEnabled, globalAmneziaEnabled ->
                    val tunnel = tunnels.firstOrNull { it.id == tunnelId }

                    Triple(
                        tunnel,
                        tunnels.filterNot { it.isGlobalConfig }.map { it.toSummary() },
                        backendStatus.activeTunnels.containsKey(tunnelId),
                    ) to
                        GlobalSettingsState(
                            dnsEnabled = globalDnsEnabled,
                            amneziaEnabled = globalAmneziaEnabled,
                        )
                }
                .collect { (backendData, globalSettings) ->
                    val (tunnel, tunnels, isRunning) = backendData

                    intent {
                        if (!state.initialized && tunnel != null) {

                            val config = EditableConfig.from(tunnel.getConfig())

                            reduce {
                                state.copy(
                                    initialized = true,
                                    isLoading = false,
                                    tunnel = tunnel,
                                    tunnels = tunnels,
                                    isRunning = isRunning,
                                    globalSettings = globalSettings,
                                    ui =
                                        ConfigEditUiState(
                                            showScripts = config.`interface`.hasScripts(),
                                            showAmneziaValues =
                                                config.`interface`.isAmneziaEnabled(),
                                        ),
                                    draft = ConfigDraft(tunnelName = tunnel.name, config = config),
                                )
                            }

                            return@intent
                        }

                        reduce {
                            state.copy(
                                isLoading = false,
                                tunnel = tunnel,
                                tunnels = tunnels,
                                isRunning = isRunning,
                            )
                        }
                    }
                }
        }

    fun save() = intent {
        reduce { state.copy(ui = state.ui.copy(showSaveModal = false)) }

        if (state.isTunnelNameTaken) {

            postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.tunnel_name_taken),
                    ToastType.Error,
                )
            )

            return@intent
        }

        if (state.draft.tunnelName.isBlank()) {
            postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.name_error_empty),
                    ToastType.Error,
                )
            )
            return@intent
        }

        runCatching {
                val config = state.draft.config.buildConfig(state.draft.tunnelName)

                config.validate()

                val quickConfig = config.asQuickString()

                val tunnelConfig =
                    if (tunnelId == null) {
                        TunnelConfig.tunnelConfFromQuick(quickConfig, state.draft.tunnelName)
                    } else {
                        state.tunnel?.copy(name = state.draft.tunnelName, quickConfig = quickConfig)
                    }

                tunnelConfig?.let {
                    tunnelRepository.save(it)

                    dnsSettingsRepository.updateGlobalDnsEnabled(state.globalSettings.dnsEnabled)

                    settingsRepository.updateGlobalAmneziaEnabled(
                        state.globalSettings.amneziaEnabled
                    )

                    if (state.isRunning) {
                        tunnelCoordinator.stopTunnel(it.id)
                        tunnelCoordinator.startTunnel(it)
                    }

                    postSideEffect(
                        GlobalSideEffect.Snackbar(
                            StringValue.StringResource(R.string.config_changes_saved),
                            ToastType.Success,
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

                postSideEffect(GlobalSideEffect.Snackbar(message, ToastType.Error))
            }
    }

    fun onRemovePeer(index: Int) = intent {
        val updatedPeers = state.draft.config.peers.toMutableList().apply { removeAt(index) }

        reduce {
            state.copy(
                draft = state.draft.copy(config = state.draft.config.copy(peers = updatedPeers))
            )
        }
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun setShowSaveModal(show: Boolean) = intent {
        reduce { state.copy(ui = state.ui.copy(showSaveModal = show)) }
    }

    fun setGlobalTunnelDnsEnabled(to: Boolean) = intent {
        reduce { state.copy(globalSettings = state.globalSettings.copy(dnsEnabled = to)) }
    }

    fun onToggleLan(index: Int) = intent {
        val updatedPeers =
            state.draft.config.peers.toMutableList().apply {
                val peer = get(index)

                val updated =
                    if (peer.isLanExcluded()) {
                        peer.includeLan()
                    } else {
                        peer.excludeLan()
                    }

                set(index, updated)
            }

        reduce {
            state.copy(
                draft = state.draft.copy(config = state.draft.config.copy(peers = updatedPeers))
            )
        }
    }

    fun onUpdatePeer(peer: EditablePeer, index: Int) = intent {
        val updatedPeers = state.draft.config.peers.toMutableList().apply { set(index, peer) }

        reduce {
            state.copy(
                draft = state.draft.copy(config = state.draft.config.copy(peers = updatedPeers))
            )
        }
    }

    fun onTunnelNameChange(name: String) = intent {
        reduce { state.copy(draft = state.draft.copy(tunnelName = name)) }
    }

    fun onAddPeer() = intent {
        reduce {
            state.copy(
                draft =
                    state.draft.copy(
                        config =
                            state.draft.config.copy(
                                peers = state.draft.config.peers + EditablePeer()
                            )
                    )
            )
        }
    }

    fun onInterfaceChange(editableInterface: EditableInterface) = intent {
        reduce {
            state.copy(
                draft =
                    state.draft.copy(
                        config = state.draft.config.copy(`interface` = editableInterface)
                    )
            )
        }
    }

    fun applyCopySource() = intent {
        val id = state.ui.selectedCopySourceTunnelId ?: return@intent

        val tunnel = tunnelRepository.getById(id) ?: return@intent

        reduce {
            state.copy(
                draft = state.draft.copy(config = EditableConfig.from(tunnel.getConfig())),
                ui = state.ui.copy(selectedCopySourceTunnelId = null),
            )
        }
    }

    fun selectCopySource(tunnelId: Int?) = intent {
        reduce { state.copy(ui = state.ui.copy(selectedCopySourceTunnelId = tunnelId)) }
    }

    fun onMimicChange(mimic: MimicMode) = intent {
        val current = state.draft.config.`interface`

        val updated =
            when (mimic) {
                MimicMode.QUIC -> current.setQuicMimic()
                MimicMode.DNS -> current.setDnsMimic()
                MimicMode.SIP -> current.setSipMimic()
            }

        onInterfaceChange(updated)
    }

    fun setGlobalAmneziaEnabled(to: Boolean) = intent {
        reduce { state.copy(globalSettings = state.globalSettings.copy(amneziaEnabled = to)) }
    }

    fun toggleShowScripts() = intent {
        reduce { state.copy(ui = state.ui.copy(showScripts = !state.ui.showScripts)) }
    }

    fun toggleShowAmneziaValues() = intent {
        reduce { state.copy(ui = state.ui.copy(showAmneziaValues = !state.ui.showAmneziaValues)) }
    }

    fun toggleAmneziaCompat() = intent {
        val current = state.draft.config.`interface`

        val (show, updated) =
            if (current.isAmneziaCompatibilityModeSet()) {
                false to current.resetAmneziaProperties()
            } else {
                true to current.toAmneziaCompatibilityConfig()
            }

        reduce {
            state.copy(
                draft = state.draft.copy(config = state.draft.config.copy(`interface` = updated)),
                ui = state.ui.copy(showAmneziaValues = show),
            )
        }
    }

    fun toggleShowSensitiveData() = intent {
        reduce { state.copy(ui = state.ui.copy(showSensitiveData = !state.ui.showSensitiveData)) }
    }

    fun setInterfaceDropdownExpanded(expanded: Boolean) = intent {
        reduce { state.copy(ui = state.ui.copy(isInterfaceDropdownExpanded = expanded)) }
    }

    fun setPeerDropdownExpanded(expanded: Boolean) = intent {
        reduce { state.copy(ui = state.ui.copy(isPeerDropdownExpanded = expanded)) }
    }
}
