package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.domain.model.InstalledPackage
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitBehavior

data class SplitTunnelUiState(
    val installedPackages: List<InstalledPackage> = emptyList(),
    val isLoading: Boolean = true,
    val tunnel: TunnelConfig? = null,
    val tunnels: List<TunnelSummary> = emptyList(),
    val activeBehavior: SplitBehavior = SplitBehavior.ALL,
    val includedPackages: Set<String> = emptySet(),
    val excludedPackages: Set<String> = emptySet(),
    val selectedCopySourceTunnelId: Int? = null,
    // Set once. Later emissions must not replace the app sets.
    val initialized: Boolean = false,
    val needsRestart: Boolean = false,
    // Null on the global editor.
    val globalOverride: SplitAppListSummary? = null,
)
