package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.domain.model.AppUpdate
import com.zaneschepke.wireguardautotunnel.domain.model.UpdateDownloadState

data class SupportUiState(
    val appUpdate: AppUpdate? = null,
    val isLoading: Boolean = false,
    val download: UpdateDownloadState = UpdateDownloadState.Idle,
)
