package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.ui.theme.Theme

data class GlobalAppUiState(
    val isAppLoaded: Boolean = false,
    val theme: Theme = Theme.AUTOMATIC,
    val pinLockEnabled: Boolean = false,
    val tunnelMode: TunnelMode = TunnelMode.VPN,
    val shouldShowDonationSnackbar: Boolean = false,
    val isDesktopPromptShown: Boolean = false,
    val isLocationDisclosureShown: Boolean = false,
    val isBatteryOptimizationShown: Boolean = false,
    val isAutoTunnelActive: Boolean = false,
    val tunnelNames: Map<Int, String> = emptyMap(),
    val selectedTunnelCount: Int = 0,
    val canMoveToGroup: Boolean = false,
    val isReorderMode: Boolean = false,
    val reorderScopeTitle: String? = null,
    val hasGroups: Boolean = false,
    val canReorder: Boolean = false,
    val hasActiveTunnel: Boolean = false,
    val alreadyDonated: Boolean = false,
    val isPinVerified: Boolean = false,
    val pendingWgImportUrl: String? = null,
    val isScreenRecordingProtectionEnabled: Boolean = false,
)
