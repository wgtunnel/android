package com.zaneschepke.wireguardautotunnel.domain.repository

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

    suspend fun getLastActiveTunnelIds(): List<Int>

    suspend fun setLastActiveTunnelIds(ids: List<Int>)

    suspend fun clearLastActiveTunnelIds()

    val flow: Flow<AppState>
}
