package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.domain.enums.StatisticRefresh

data class MonitoringUiState(
    val tunnelStatisticsEnabled: Boolean = false,
    val statisticRefresh: StatisticRefresh = StatisticRefresh.BALANCED,
    val isLoading: Boolean = true,
)
