package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.dokar.sonner.ToastType
import com.wgtunnel.backend.shell.ShellExecutor
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.shortcut.ShortcutManager
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.SettingUiState
import com.zaneschepke.wireguardautotunnel.util.StringValue
import java.util.UUID
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

class SettingsViewModel(
    private val settingsRepository: GeneralSettingRepository,
    private val shortcutManager: ShortcutManager,
    private val tunnelsRepository: TunnelRepository,
    private val monitoringRepository: MonitoringSettingsRepository,
    private val globalEffectRepository: GlobalEffectRepository,
    private val tunnelCoordinator: TunnelCoordinator,
) : OrbitContainerHost<SettingUiState, SettingUiState, Nothing>, ViewModel() {

    override val container =
        orbitContainer<SettingUiState, Nothing>(
            SettingUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            intent {
                combine(
                        settingsRepository.flow,
                        tunnelsRepository.globalTunnelFlow,
                        tunnelsRepository.userTunnelsFlow,
                        monitoringRepository.flow,
                        tunnelCoordinator.backendStatus
                            .map { status ->
                                val active = status.activeTunnels.values
                                Triple(
                                    active.isNotEmpty(),
                                    active.sumOf { it.recoveryAttempts },
                                    active.maxOfOrNull { it.lastRecoveryAttemptMs } ?: 0L,
                                )
                            }
                            .distinctUntilChanged(),
                    ) { settings, tunnel, tunnels, monitoring, recovery ->
                        val (tunnelActive, recoveryEventCount, lastRecoveryEventMs) = recovery
                        state.copy(
                            settings = settings,
                            remoteKey = settings.remoteKey,
                            isRemoteEnabled = settings.isRemoteControlEnabled,
                            isPinLockEnabled = settings.isPinLockEnabled,
                            isLoading = false,
                            tunnelActive = tunnelActive,
                            globalTunnelConfig = tunnel,
                            monitoring = monitoring,
                            tunnels = tunnels,
                            recoveryEventCount = recoveryEventCount,
                            lastRecoveryEventMs = lastRecoveryEventMs,
                        )
                    }
                    .collect { reduce { it } }
            }
        }

    fun setShortcutsEnabled(to: Boolean) = intent {
        if (to) shortcutManager.addShortcuts() else shortcutManager.removeShortcuts()
        settingsRepository.upsert(state.settings.copy(isShortcutsEnabled = to))
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun setAlwaysOnVpnEnabled(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(isAlwaysOnVpnEnabled = to))
    }

    fun setLiveUpdatesEnabled(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(isLiveUpdatesEnabled = to))
    }

    fun setNotificationOriginEnabled(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(isNotificationOriginEnabled = to))
    }

    fun setNotificationTransferEnabled(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(isNotificationTransferEnabled = to))
    }

    fun setNotificationRecoveryEnabled(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(isNotificationRecoveryEnabled = to))
    }

    fun setNotificationFailureTintEnabled(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(isNotificationFailureTintEnabled = to))
    }

    fun setRestoreOnBootEnabled(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(isRestoreOnBootEnabled = to))
    }

    fun setGlobalSplitTunneling(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(isGlobalSplitTunnelEnabled = to))
    }

    fun setLocalLogging(to: Boolean) = intent {
        monitoringRepository.upsert(state.monitoring.copy(isLocalLogsEnabled = to))
    }

    fun setRemoteEnabled(to: Boolean) = intent {
        settingsRepository.upsert(
            state.settings.copy(
                isRemoteControlEnabled = to,
                remoteKey = UUID.randomUUID().toString(),
            )
        )
    }

    fun setTunnelScriptedEnabled(to: Boolean) = intent {
        if (to) {
            val accepted = ShellExecutor.requestPrivilegedAccess()
            if (!accepted)
                return@intent postSideEffect(
                    GlobalSideEffect.Snackbar(
                        StringValue.StringResource(R.string.error_root_denied),
                        ToastType.Error,
                    )
                )
            postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.root_accepted),
                    ToastType.Success,
                )
            )
        }
        settingsRepository.upsert(state.settings.copy(tunnelScriptingEnabled = to))
    }

    fun setSeamlessRecovery(enabled: Boolean) = intent {
        settingsRepository.updateSeamlessRecovery(enabled)
    }

    fun setSeamlessRecoveryBounceDelay(seconds: Int) = intent {
        settingsRepository.updateSeamlessRecoveryBounceDelay(seconds)
    }

    fun setAlreadyDonated(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(alreadyDonated = to))
    }
}
