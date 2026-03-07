package com.zaneschepke.wireguardautotunnel.data.repository

import com.zaneschepke.wireguardautotunnel.data.DataStoreManager
import com.zaneschepke.wireguardautotunnel.data.entity.AppState as Entity
import com.zaneschepke.wireguardautotunnel.data.mapper.toDomain
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

    override suspend fun setShouldShowDonationSnackbar(show: Boolean) {
        dataStoreManager.saveToDataStore(DataStoreManager.shouldShowDonationSnackbar, show)
    }

    override suspend fun shouldShowDonationSnackbar(): Boolean {
        return dataStoreManager.getFromStore(DataStoreManager.shouldShowDonationSnackbar) ?: false
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
                            shouldShowDonationSnackbar =
                                pref[DataStoreManager.shouldShowDonationSnackbar] ?: false,
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
