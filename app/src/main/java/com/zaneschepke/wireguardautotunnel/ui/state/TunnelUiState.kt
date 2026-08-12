package com.zaneschepke.wireguardautotunnel.ui.state

import com.wgtunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

data class TunnelUiState(
    val tunnel: TunnelConfig? = null,
    val activeConfig: ActiveConfig? = null,
    val includedAppsCount: Int? = null,
    val excludedAppsCount: Int? = null,
    val isLoading: Boolean = true,
)
