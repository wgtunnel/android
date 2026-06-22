package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.domain.enums.StatisticRefresh
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.ui.state.MonitoringUiState
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class MonitoringViewModel(private val monitoringSettingsRepository: MonitoringSettingsRepository) :
    ContainerHost<MonitoringUiState, Nothing>, ViewModel() {

    override val container =
        container<MonitoringUiState, Nothing>(
            MonitoringUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            monitoringSettingsRepository.flow.collect {
                val statisticRefresh = StatisticRefresh.fromValue(it.tunnelStatisticsPollInterval)
                reduce {
                    state.copy(
                        statisticRefresh = statisticRefresh,
                        tunnelStatisticsEnabled = it.tunnelStatisticsEnabled,
                        isLoading = false,
                    )
                }
            }
        }

    fun onLiveTunnelStatisticsChanged(to: Boolean) = intent {
        monitoringSettingsRepository.updateStatisticsEnabled(to)
    }

    fun onStatisticsIntervalChanged(statisticRefresh: StatisticRefresh) = intent {
        monitoringSettingsRepository.updateStatisticRefresh(statisticRefresh.value)
    }
}
