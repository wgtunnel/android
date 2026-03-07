package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.core.shortcut.ShortcutManager
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.SettingUiState
import java.util.UUID
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class SettingsViewModel(
    private val settingsRepository: GeneralSettingRepository,
    private val shortcutManager: ShortcutManager,
    private val tunnelsRepository: TunnelRepository,
    private val monitoringRepository: MonitoringSettingsRepository,
    private val globalEffectRepository: GlobalEffectRepository,
    private val tunnelManager: TunnelManager,
) : ContainerHost<SettingUiState, Nothing>, ViewModel() {

    override val container =
        container<SettingUiState, Nothing>(
            SettingUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            intent {
                combine(
                        settingsRepository.flow,
                        tunnelsRepository.globalTunnelFlow,
                        tunnelsRepository.userTunnelsFlow,
                        monitoringRepository.flow,
                        tunnelManager.activeTunnels.map { it.isNotEmpty() }.distinctUntilChanged(),
                    ) { settings, tunnel, tunnels, monitoring, tunnelActive ->
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

    fun setRestoreOnBootEnabled(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(isRestoreOnBootEnabled = to))
    }

    fun setGlobalSplitTunneling(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(isGlobalSplitTunnelEnabled = to))
        if (state.globalTunnelConfig == null)
            tunnelsRepository.save(TunnelConfig.generateDefaultGlobalConfig())
    }

    fun setLocalLogging(to: Boolean) = intent {
        monitoringRepository.upsert(state.monitoring.copy(isLocalLogsEnabled = to))
    }

    fun setPingEnabled(to: Boolean) = intent {
        monitoringRepository.upsert(state.monitoring.copy(isPingEnabled = to))
    }

    fun setRemoteEnabled(to: Boolean) = intent {
        settingsRepository.upsert(
            state.settings.copy(
                isRemoteControlEnabled = to,
                remoteKey = UUID.randomUUID().toString(),
            )
        )
    }

    fun setAlreadyDonated(to: Boolean) = intent {
        settingsRepository.upsert(state.settings.copy(alreadyDonated = to))
    }
}
