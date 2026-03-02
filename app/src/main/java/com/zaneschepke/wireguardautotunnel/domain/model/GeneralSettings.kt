package com.zaneschepke.wireguardautotunnel.domain.model

import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.ui.theme.Theme

data class GeneralSettings(
    val id: Int = 0,
    val isShortcutsEnabled: Boolean = false,
    val isRestoreOnBootEnabled: Boolean = false,
    val isMultiTunnelEnabled: Boolean = false,
    val isGlobalSplitTunnelEnabled: Boolean = false,
    val appMode: AppMode = AppMode.fromValue(0),
    val theme: Theme = Theme.AUTOMATIC,
    val locale: String? = null,
    val remoteKey: String? = null,
    val isRemoteControlEnabled: Boolean = false,
    val isPinLockEnabled: Boolean = false,
    val isAlwaysOnVpnEnabled: Boolean = false,
    val isKillSwitchMetered: Boolean = true,
    val alreadyDonated: Boolean = false,
    val isLanBypassEnabled: Boolean = false,
)
