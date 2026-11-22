package com.zaneschepke.wireguardautotunnel.data.repository

import com.zaneschepke.wireguardautotunnel.data.DataStoreManager
import com.zaneschepke.wireguardautotunnel.data.entity.AppState as Entity
import com.zaneschepke.wireguardautotunnel.data.mapper.toDomain
import com.zaneschepke.wireguardautotunnel.domain.enums.StrictSplitNotice
import com.zaneschepke.wireguardautotunnel.domain.model.AppState as Domain
import com.zaneschepke.wireguardautotunnel.domain.repository.AppStateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import timber.log.Timber

class DataStoreAppStateRepository(
    private val dataStoreManager: DataStoreManager,
    applicationScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
) : AppStateRepository {
    override suspend fun isLocationDisclosureShown(): Boolean {
        return dataStoreManager.getFromStore(DataStoreManager.locationDisclosureShown) ?: false
    }

    override suspend fun setLocationDisclosureShown(shown: Boolean) {
        dataStoreManager.saveToDataStore(DataStoreManager.locationDisclosureShown, shown)
    }

    override suspend fun isBatteryOptimizationDisableShown(): Boolean {
        return dataStoreManager.getFromStore(DataStoreManager.batteryDisableShown) ?: false
    }

    override suspend fun setBatteryOptimizationDisableShown(shown: Boolean) {
        dataStoreManager.saveToDataStore(DataStoreManager.batteryDisableShown, shown)
    }

    override suspend fun isNotificationPermissionRequested(): Boolean {
        return dataStoreManager.getFromStore(DataStoreManager.notificationPermissionRequested)
            ?: false
    }

    override suspend fun setNotificationPermissionRequested(requested: Boolean) {
        dataStoreManager.saveToDataStore(
            DataStoreManager.notificationPermissionRequested,
            requested,
        )
    }

    override suspend fun setShouldShowDonationSnackbar(show: Boolean) {
        dataStoreManager.saveToDataStore(DataStoreManager.shouldShowDonationSnackbar, show)
    }

    override suspend fun shouldShowDonationSnackbar(): Boolean {
        return dataStoreManager.getFromStore(DataStoreManager.shouldShowDonationSnackbar) ?: false
    }

    override suspend fun isDesktopPromptShown(): Boolean {
        return dataStoreManager.getFromStore(DataStoreManager.desktopPromptShown) ?: false
    }

    override suspend fun setDesktopPromptShown(shown: Boolean) {
        dataStoreManager.saveToDataStore(DataStoreManager.desktopPromptShown, shown)
    }

    override suspend fun isStrictSplitDecided(): Boolean {
        return dataStoreManager.getFromStore(DataStoreManager.strictSplitDecided) ?: false
    }

    override suspend fun setStrictSplitDecided(decided: Boolean) {
        dataStoreManager.saveToDataStore(DataStoreManager.strictSplitDecided, decided)
    }

    override val strictSplitNotice: Flow<StrictSplitNotice?> =
        dataStoreManager.getFromStoreFlow(DataStoreManager.strictSplitNotice).map { name ->
            StrictSplitNotice.entries.firstOrNull { it.name == name }
        }

    override suspend fun setStrictSplitNotice(notice: StrictSplitNotice?) {
        if (notice == null) {
            dataStoreManager.removeFromDataStore(DataStoreManager.strictSplitNotice)
        } else {
            dataStoreManager.saveToDataStore(DataStoreManager.strictSplitNotice, notice.name)
        }
    }

    override suspend fun getLastActiveTunnelIds(): List<Int> {
        val stored = dataStoreManager.getFromStore(DataStoreManager.lastActiveTunnelIds).orEmpty()
        if (stored.isBlank()) return emptyList()
        return stored.split(',').mapNotNull { it.toIntOrNull() }
    }

    override suspend fun setLastActiveTunnelIds(ids: List<Int>) {
        if (ids.isEmpty()) {
            clearLastActiveTunnelIds()
            return
        }
        dataStoreManager.saveToDataStore(
            DataStoreManager.lastActiveTunnelIds,
            ids.distinct().joinToString(","),
        )
    }

    override suspend fun clearLastActiveTunnelIds() {
        dataStoreManager.saveToDataStore(DataStoreManager.lastActiveTunnelIds, "")
    }

    override suspend fun getLastUsedTunnelIds(): List<Int> {
        val stored = dataStoreManager.getFromStore(DataStoreManager.lastUsedTunnelIds).orEmpty()
        if (stored.isBlank()) return emptyList()
        return stored.split(',').mapNotNull { it.toIntOrNull() }
    }

    override suspend fun setLastUsedTunnelIds(ids: List<Int>) {
        if (ids.isEmpty()) return
        dataStoreManager.saveToDataStore(
            DataStoreManager.lastUsedTunnelIds,
            ids.distinct().joinToString(","),
        )
    }

    override val flow: Flow<Domain> =
        dataStoreManager.preferencesFlow
            .map { prefs ->
                prefs?.let { pref ->
                    try {
                        Entity(
                            isLocationDisclosureShown =
                                pref[DataStoreManager.locationDisclosureShown] ?: false,
                            isBatteryOptimizationDisableShown =
                                pref[DataStoreManager.batteryDisableShown] ?: false,
                            isNotificationPermissionRequested =
                                pref[DataStoreManager.notificationPermissionRequested] ?: false,
                            shouldShowDonationSnackbar =
                                pref[DataStoreManager.shouldShowDonationSnackbar] ?: false,
                            isDesktopPromptShown =
                                pref[DataStoreManager.desktopPromptShown] ?: false,
                        )
                    } catch (e: IllegalArgumentException) {
                        Timber.e(e)
                        Entity()
                    }
                } ?: Entity()
            }
            .map { it.toDomain() }
            .stateIn(
                scope = applicationScope + ioDispatcher,
                started = SharingStarted.Eagerly,
                initialValue = com.zaneschepke.wireguardautotunnel.domain.model.AppState(),
            )
}
