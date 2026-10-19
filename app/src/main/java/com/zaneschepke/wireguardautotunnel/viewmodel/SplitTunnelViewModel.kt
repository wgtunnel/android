package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.dokar.sonner.ToastType
import com.wgtunnel.backend.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.InstalledPackageRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitBehavior
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.splitBehaviorOf
import com.zaneschepke.wireguardautotunnel.ui.state.EditableConfig
import com.zaneschepke.wireguardautotunnel.ui.state.EditableInterface
import com.zaneschepke.wireguardautotunnel.ui.state.SplitAppListSummary
import com.zaneschepke.wireguardautotunnel.ui.state.SplitTunnelUiState
import com.zaneschepke.wireguardautotunnel.ui.state.splitAppListSummary
import com.zaneschepke.wireguardautotunnel.util.StringValue
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

class SplitTunnelViewModel(
    private val tunnelRepository: TunnelRepository,
    private val packageRepository: InstalledPackageRepository,
    private val globalEffectRepository: GlobalEffectRepository,
    private val settingsRepository: GeneralSettingRepository,
    private val tunnelCoordinator: TunnelCoordinator,
    val tunnelId: Int,
) : OrbitContainerHost<SplitTunnelUiState, SplitTunnelUiState, Nothing>, ViewModel() {

    override val container =
        orbitContainer<SplitTunnelUiState, Nothing>(
            SplitTunnelUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            val packagesFlow = packageRepository.installedPackages

            val tunnelsFlow = tunnelRepository.userTunnelsFlow

            val currentTunnelFlow =
                tunnelRepository.flow.map { list -> list.firstOrNull { it.id == tunnelId } }

            // Seed the app sets once, inside reduce. Later emissions must not replace them.
            combine(
                    combine(packagesFlow, tunnelsFlow, currentTunnelFlow) {
                        packages,
                        tunnels,
                        tunnel ->
                        Triple(packages, tunnels, tunnel)
                    },
                    combine(settingsRepository.flow, tunnelRepository.globalTunnelFlow) {
                        settings,
                        global ->
                        settings to global
                    },
                    tunnelCoordinator.backendStatus,
                ) { lists, settingsAndGlobal, status ->
                    Triple(lists, settingsAndGlobal, status)
                }
                .collect { (lists, settingsAndGlobal, status) ->
                    val (settings, globalTunnel) = settingsAndGlobal
                    reduce {
                        val (packages, tunnels, incoming) = lists
                        val tunnel = incoming ?: state.tunnel
                        val restart = needsRestart(tunnel, settings, status)
                        val override = globalOverrideSummary(tunnel, settings, globalTunnel)
                        if (!state.initialized && incoming != null) {
                            val config = incoming.getConfig()
                            val included = config.`interface`.allIncludedApps.toSet()
                            val excluded = config.`interface`.allExcludedApps.toSet()
                            state.copy(
                                initialized = true,
                                installedPackages = packages,
                                tunnels = tunnels.map { it.toSummary() },
                                tunnel = incoming,
                                isLoading = packages.isEmpty(),
                                activeBehavior = splitBehaviorOf(included, excluded),
                                includedPackages = included,
                                excludedPackages = excluded,
                                needsRestart = restart,
                                globalOverride = override,
                            )
                        } else {
                            state.copy(
                                installedPackages = packages,
                                tunnels = tunnels.map { it.toSummary() },
                                tunnel = tunnel,
                                isLoading = packages.isEmpty() || !state.initialized,
                                needsRestart = restart,
                                globalOverride = override,
                            )
                        }
                    }
                }
        }

    private fun globalOverrideSummary(
        tunnel: TunnelConfig?,
        settings: GeneralSettings,
        global: TunnelConfig?,
    ): SplitAppListSummary? {
        if (tunnel == null || tunnel.isGlobalConfig) return null
        if (!settings.isGlobalSplitTunnelEnabled || global == null) return null
        val iface = global.getConfig().`interface`
        return splitAppListSummary(
            includedCount = iface.allIncludedApps.size,
            excludedCount = iface.allExcludedApps.size,
            emptyMeansAllTunneled = true,
        )
    }

    private fun needsRestart(
        tunnel: TunnelConfig?,
        settings: GeneralSettings,
        status: BackendStatus,
    ): Boolean {
        if (tunnel == null || settings.tunnelMode != TunnelMode.VPN) return false
        val activeIds = status.activeTunnels.keys
        // Only when this save changes the list a running VPN will use.
        return if (tunnel.isGlobalConfig) {
            settings.isGlobalSplitTunnelEnabled && activeIds.isNotEmpty()
        } else {
            !settings.isGlobalSplitTunnelEnabled && tunnel.id in activeIds
        }
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun save(restart: Boolean = false) = intent {
        val current = state
        val tunnel = current.tunnel ?: return@intent
        val config = tunnel.getConfig()
        val editableConfig = EditableConfig.from(config)
        val editableInterface = EditableInterface.from(config.`interface`)
        val (included, excluded) =
            when (current.activeBehavior) {
                SplitBehavior.ALL -> Pair(emptySet(), emptySet())
                SplitBehavior.INCLUDE -> Pair(current.includedPackages, emptySet<String>())
                SplitBehavior.EXCLUDE -> Pair(emptySet<String>(), current.excludedPackages)
            }
        val updatedInterface =
            editableInterface.copy(includedApplications = included, excludedApplications = excluded)
        val updatedProxyConfig = editableConfig.copy(`interface` = updatedInterface)
        val updatedConfig = updatedProxyConfig.buildConfig()
        val updatedTunnel =
            tunnel.copy(quickConfig = updatedConfig.withName(tunnel.name).asQuickString())
        tunnelRepository.save(updatedTunnel)
        if (restart) {
            if (updatedTunnel.isGlobalConfig) {
                tunnelCoordinator.restartActiveTunnels()
            } else {
                tunnelCoordinator.stopTunnel(updatedTunnel.id)
                tunnelCoordinator.startTunnel(updatedTunnel)
            }
        }
        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.config_changes_saved),
                ToastType.Success,
            )
        )
        postSideEffect(GlobalSideEffect.PopBackStack)
    }

    fun setActiveBehavior(behavior: SplitBehavior) = intent {
        reduce { state.copy(activeBehavior = behavior) }
    }

    fun togglePackage(pkg: String, enabled: Boolean) = intent {
        reduce {
            when (state.activeBehavior) {
                SplitBehavior.ALL -> state
                SplitBehavior.INCLUDE -> {
                    val updated =
                        state.includedPackages.toMutableSet().apply {
                            if (enabled) add(pkg) else remove(pkg)
                        }
                    state.copy(includedPackages = updated)
                }
                SplitBehavior.EXCLUDE -> {
                    val updated =
                        state.excludedPackages.toMutableSet().apply {
                            if (enabled) add(pkg) else remove(pkg)
                        }
                    state.copy(excludedPackages = updated)
                }
            }
        }
    }

    fun applyCopySource() = intent {
        val id = state.selectedCopySourceTunnelId ?: return@intent

        val tunnel = tunnelRepository.getById(id) ?: return@intent

        val config = tunnel.getConfig()
        val included = config.`interface`.allIncludedApps.toSet()
        val excluded = config.`interface`.allExcludedApps.toSet()
        reduce {
            state.copy(
                activeBehavior = splitBehaviorOf(included, excluded),
                includedPackages = included,
                excludedPackages = excluded,
                selectedCopySourceTunnelId = null,
            )
        }
    }

    fun selectCopySource(tunnelId: Int?) = intent {
        reduce { state.copy(selectedCopySourceTunnelId = tunnelId) }
    }
}
