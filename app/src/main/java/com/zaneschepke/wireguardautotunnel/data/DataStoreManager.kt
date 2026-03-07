package com.zaneschepke.wireguardautotunnel.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

class DataStoreManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val preferencesKey = "preferences"
    val Context.dataStore by preferencesDataStore(name = preferencesKey)
    val dataStore = context.dataStore

    companion object {
        val locationDisclosureShown = booleanPreferencesKey("LOCATION_DISCLOSURE_SHOWN")
        val batteryDisableShown = booleanPreferencesKey("BATTERY_OPTIMIZE_DISABLE_SHOWN")
        val shouldShowDonationSnackbar = booleanPreferencesKey("SHOW_DONATION_SNACK")
    }

    suspend fun init() {
        withContext(ioDispatcher) {
            try {
                dataStore.data.first()
            } catch (e: IOException) {
                Timber.e(e)
            }
        }
    }

    suspend fun <T> saveToDataStore(key: Preferences.Key<T>, value: T) {
        withContext(ioDispatcher) {
            try {
                dataStore.edit { it[key] = value }
            } catch (e: IOException) {
                Timber.e(e)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    suspend fun <T> removeFromDataStore(key: Preferences.Key<T>) {
        withContext(ioDispatcher) {
            try {
                dataStore.edit { it.remove(key) }
            } catch (e: IOException) {
                Timber.e(e)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    fun <T> getFromStoreFlow(key: Preferences.Key<T>) = context.dataStore.data.map { it[key] }

    suspend fun <T> getFromStore(key: Preferences.Key<T>): T? {
        return withContext(ioDispatcher) {
            try {
                dataStore.data.map { it[key] }.first()
            } catch (e: IOException) {
                Timber.e(e)
                null
            }
        }
    }

    val preferencesFlow: Flow<Preferences?> = dataStore.data.flowOn(ioDispatcher)
}
