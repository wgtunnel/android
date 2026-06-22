package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.InstalledPackageRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitOption
import com.zaneschepke.wireguardautotunnel.ui.state.EditableConfig
import com.zaneschepke.wireguardautotunnel.ui.state.EditableInterface
import com.zaneschepke.wireguardautotunnel.ui.state.SplitTunnelUiState
import com.zaneschepke.wireguardautotunnel.util.StringValue
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class SplitTunnelViewModel(
    private val tunnelRepository: TunnelRepository,
    private val packageRepository: InstalledPackageRepository,
    private val globalEffectRepository: GlobalEffectRepository,
    val tunnelId: Int,
) : ContainerHost<SplitTunnelUiState, Nothing>, ViewModel() {

    override val container =
        container<SplitTunnelUiState, Nothing>(
            SplitTunnelUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            val packagesFlow = flow { emit(packageRepository.getInstalledPackages()) }

            val tunnelsFlow = tunnelRepository.userTunnelsFlow

            val currentTunnelFlow =
                tunnelRepository.flow.map { list -> list.firstOrNull { it.id == tunnelId } }

            combine(packagesFlow, tunnelsFlow, currentTunnelFlow) { packages, tunnels, tunnel ->
                    val currentState = state

                    val config = tunnel?.getConfig()

                    val (initialOption, initialPkgs) =
                        when {
                            config?.`interface`?.allExcludedApps?.isNotEmpty() == true ->
                                SplitOption.EXCLUDE to config.`interface`.allExcludedApps.toSet()

                            config?.`interface`?.allIncludedApps?.isNotEmpty() == true ->
                                SplitOption.INCLUDE to config.`interface`.allIncludedApps.toSet()

                            else -> SplitOption.ALL to emptySet()
                        }

                    val isInitialized = currentState.tunnel != null

                    SplitTunnelUiState(
                        installedPackages = packages,
                        tunnels = tunnels.map { it.toSummary() },
                        tunnel = tunnel,
                        isLoading = false,
                        splitOption =
                            if (isInitialized) currentState.splitOption else initialOption,
                        selectedPackages =
                            if (isInitialized) currentState.selectedPackages else initialPkgs,
                        selectedCopySourceTunnelId = currentState.selectedCopySourceTunnelId,
                    )
                }
                .collect { newState -> reduce { newState } }
        }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun save() = intent {
        val tunnel = state.tunnel ?: return@intent
        val config = tunnel.getConfig()
        val editableConfig = EditableConfig.from(config)
        val editableInterface = EditableInterface.from(config.`interface`)
        val (included, excluded) =
            when (state.splitOption) {
                SplitOption.INCLUDE -> Pair(state.selectedPackages, emptySet<String>())
                SplitOption.ALL -> Pair(emptySet(), emptySet())
                SplitOption.EXCLUDE -> Pair(emptySet(), state.selectedPackages)
            }
        val updatedInterface =
            editableInterface.copy(includedApplications = included, excludedApplications = excluded)
        val updatedProxyConfig = editableConfig.copy(`interface` = updatedInterface)
        val updatedConfig = updatedProxyConfig.buildConfig()
        tunnelRepository.save(
            tunnel.copy(quickConfig = updatedConfig.withName(tunnel.name).asQuickString())
        )
        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.config_changes_saved),
                ToastType.Success,
            )
        )
        postSideEffect(GlobalSideEffect.PopBackStack)
    }

    fun setSplitOption(option: SplitOption) = intent { reduce { state.copy(splitOption = option) } }

    fun togglePackage(pkg: String, enabled: Boolean) = intent {
        reduce {
            val updated =
                state.selectedPackages.toMutableSet().apply {
                    if (enabled) add(pkg) else remove(pkg)
                }
            state.copy(selectedPackages = updated)
        }
    }

    fun applyCopySource() = intent {
        val id = state.selectedCopySourceTunnelId ?: return@intent

        val tunnel = tunnelRepository.getById(id) ?: return@intent

        val config = tunnel.getConfig()

        val (option, pkgs) =
            when {
                config.`interface`.allExcludedApps.isNotEmpty() ->
                    SplitOption.EXCLUDE to config.`interface`.allExcludedApps.toSet()

                config.`interface`.allIncludedApps.isNotEmpty() ->
                    SplitOption.INCLUDE to config.`interface`.allIncludedApps.toSet()

                else -> SplitOption.ALL to emptySet()
            }

        reduce {
            state.copy(
                splitOption = option,
                selectedPackages = pkgs,
                selectedCopySourceTunnelId = null,
            )
        }
    }

    fun selectCopySource(tunnelId: Int?) = intent {
        reduce { state.copy(selectedCopySourceTunnelId = tunnelId) }
    }
}
