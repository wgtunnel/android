package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.enums.StrictSplitNotice
import com.zaneschepke.wireguardautotunnel.domain.model.AppState
import kotlinx.coroutines.flow.Flow

interface AppStateRepository {
    suspend fun isLocationDisclosureShown(): Boolean

    suspend fun setLocationDisclosureShown(shown: Boolean)

    suspend fun isBatteryOptimizationDisableShown(): Boolean

    suspend fun setBatteryOptimizationDisableShown(shown: Boolean)

    suspend fun isNotificationPermissionRequested(): Boolean

    suspend fun setNotificationPermissionRequested(requested: Boolean)

    suspend fun setShouldShowDonationSnackbar(show: Boolean)

    suspend fun shouldShowDonationSnackbar(): Boolean

    suspend fun isDesktopPromptShown(): Boolean

    suspend fun setDesktopPromptShown(shown: Boolean)

    suspend fun isStrictSplitDecided(): Boolean

    suspend fun setStrictSplitDecided(decided: Boolean)

    val strictSplitNotice: Flow<StrictSplitNotice?>

    suspend fun setStrictSplitNotice(notice: StrictSplitNotice?)

    suspend fun getLastActiveTunnelIds(): List<Int>

    suspend fun setLastActiveTunnelIds(ids: List<Int>)

    suspend fun clearLastActiveTunnelIds()

    suspend fun getLastUsedTunnelIds(): List<Int>

    suspend fun setLastUsedTunnelIds(ids: List<Int>)

    val flow: Flow<AppState>
}
