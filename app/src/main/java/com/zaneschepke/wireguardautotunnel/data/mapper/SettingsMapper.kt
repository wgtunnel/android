package com.zaneschepke.wireguardautotunnel.data.mapper

import com.zaneschepke.wireguardautotunnel.data.entity.GeneralSettings as Entity
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings as Domain
import com.zaneschepke.wireguardautotunnel.ui.theme.Theme

fun Entity.toDomain(): Domain =
    Domain(
        id = id,
        isShortcutsEnabled = isShortcutsEnabled,
        isRestoreOnBootEnabled = isRestoreOnBootEnabled,
        isMultiTunnelEnabled = isMultiTunnelEnabled,
        isGlobalSplitTunnelEnabled = isGlobalSplitTunnelEnabled,
        appMode = appMode,
        theme = Theme.valueOf(theme.uppercase()),
        locale = locale,
        remoteKey = remoteKey,
        isRemoteControlEnabled = isRemoteControlEnabled,
        isPinLockEnabled = isPinLockEnabled,
        isAlwaysOnVpnEnabled = isAlwaysOnVpnEnabled,
        alreadyDonated = alreadyDonated,
        isLanBypassEnabled = isLanBypassEnabled,
    )

fun Domain.toEntity(): Entity =
    Entity(
        id = id,
        isShortcutsEnabled = isShortcutsEnabled,
        isRestoreOnBootEnabled = isRestoreOnBootEnabled,
        isMultiTunnelEnabled = isMultiTunnelEnabled,
        isGlobalSplitTunnelEnabled = isGlobalSplitTunnelEnabled,
        appMode = appMode,
        theme = theme.name,
        locale = locale,
        remoteKey = remoteKey,
        isRemoteControlEnabled = isRemoteControlEnabled,
        isPinLockEnabled = isPinLockEnabled,
        isAlwaysOnVpnEnabled = isAlwaysOnVpnEnabled,
        alreadyDonated = alreadyDonated,
        isLanBypassEnabled = isLanBypassEnabled,
    )
