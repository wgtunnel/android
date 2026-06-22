package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.logcatter.model.LogMessage
import com.zaneschepke.wireguardautotunnel.domain.model.MonitoringSettings

data class LoggerUiState(
    val messages: List<LogMessage> = emptyList(),
    val monitoringSettings: MonitoringSettings = MonitoringSettings(),
    val isLoading: Boolean = true,
)
