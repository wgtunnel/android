package com.zaneschepke.wireguardautotunnel.core.lock

import android.os.SystemClock
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.lifecyle.AppVisibilityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppLockManager(
    private val settingsRepository: GeneralSettingRepository,
    appVisibilityObserver: AppVisibilityObserver,
    applicationScope: CoroutineScope,
) {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    val lockEnabled: StateFlow<Boolean> =
        settingsRepository.flow
            .map { it.isPinLockEnabled }
            .stateIn(applicationScope, SharingStarted.Eagerly, false)

    @Volatile private var backgroundedAt: Long? = null

    init {
        applicationScope.launch {
            appVisibilityObserver.isForeground.collect { foreground ->
                if (foreground) {
                    relockIfExpired()
                    backgroundedAt = null
                } else {
                    backgroundedAt = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    fun unlock() {
        _unlocked.value = true
    }

    /** True when app lock is on and the PIN has not been entered recently. */
    suspend fun isLockRequired(): Boolean {
        if (!settingsRepository.getGeneralSettings().isPinLockEnabled) return false
        relockIfExpired()
        return !_unlocked.value
    }

    private fun relockIfExpired() {
        val since = backgroundedAt ?: return
        if (SystemClock.elapsedRealtime() - since >= RELOCK_AFTER_MS) _unlocked.value = false
    }

    companion object {
        // Short trips out of the app, like a file picker or the VPN consent dialog, stay unlocked
        const val RELOCK_AFTER_MS = 60_000L
    }
}
