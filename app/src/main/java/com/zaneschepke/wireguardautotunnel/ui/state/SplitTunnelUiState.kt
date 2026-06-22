package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.domain.model.InstalledPackage
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitOption

data class SplitTunnelUiState(
    val installedPackages: List<InstalledPackage> = emptyList(),
    val isLoading: Boolean = true,
    val tunnel: TunnelConfig? = null,
    val tunnels: List<TunnelSummary> = emptyList(),
    val splitOption: SplitOption = SplitOption.ALL,
    val selectedPackages: Set<String> = emptySet(),
    val selectedCopySourceTunnelId: Int? = null,
)
