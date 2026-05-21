package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig

data class TunnelUiState(
    val tunnel: TunnelConfig? = null,
    val activeConfig: ActiveConfig? = null,
    val includedAppsCount: Int? = null,
    val excludedAppsCount: Int? = null,
    val isLoading: Boolean = true,
)
