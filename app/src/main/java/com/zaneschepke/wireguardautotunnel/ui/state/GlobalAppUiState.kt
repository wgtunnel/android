package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.ui.theme.Theme
import com.zaneschepke.wireguardautotunnel.util.LocaleUtil

data class GlobalAppUiState(
    val isAppLoaded: Boolean = false,
    val theme: Theme = Theme.AUTOMATIC,
    val locale: String = LocaleUtil.OPTION_PHONE_LANGUAGE,
    val pinLockEnabled: Boolean = false,
    val appMode: AppMode = AppMode.VPN,
    val shouldShowDonationSnackbar: Boolean = false,
    val isLocationDisclosureShown: Boolean = false,
    val isBatteryOptimizationShown: Boolean = false,
    val isAutoTunnelActive: Boolean = false,
    val tunnelNames: Map<Int, String> = emptyMap(),
    val selectedTunnelCount: Int = 0,
    val alreadyDonated: Boolean = false,
    val isPinVerified: Boolean = false,
)
