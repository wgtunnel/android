package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.logcatter.LogReader
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.repository.DnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.LockdownSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import timber.log.Timber

class AppBoostrapCoordinator(
    private val monitoringRepository: MonitoringSettingsRepository,
    private val settingsRepository: GeneralSettingRepository,
    private val dnsRepository: DnsSettingsRepository,
    private val tunnelRepository: TunnelRepository,
    private val lockdownRepository: LockdownSettingsRepository,
    private val tunnelProvider: TunnelProvider,
    private val dnsSettingsCoordinator: DnsSettingsCoordinator,
    private val logReader: LogReader,
) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    suspend fun bootstrap() = coroutineScope {
        launch { bootstrapLogging() }

        val criticalTasks =
            listOf(
                async { bootstrapDns() },
                async { ensureGlobalConfig() },
                async { restoreLockdown() },
            )

        try {
            criticalTasks.awaitAll()
            _isReady.value = true
            Timber.d("App bootstrap completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "One or more critical bootstrap tasks failed")
            _isReady.value = true
        }
    }

    private suspend fun bootstrapDns() {
        val dnsSettings = dnsRepository.getDnsSettings()
        dnsSettingsCoordinator.appyDnsSettings(dnsSettings)
    }

    private suspend fun bootstrapLogging() {
        monitoringRepository.flow
            .distinctUntilChangedBy { it.isLocalLogsEnabled }
            .collect { settings ->
                if (settings.isLocalLogsEnabled) {
                    logReader.start()
                } else {
                    logReader.stop()
                }
            }
    }

    private suspend fun ensureGlobalConfig() {
        tunnelRepository.ensureGlobalConfigExists()
    }

    private suspend fun restoreLockdown() {
        val settings = settingsRepository.getGeneralSettings()

        when (settings.tunnelMode) {
            TunnelMode.LOCK_DOWN -> {
                val lockdownSettings = lockdownRepository.getLockdownSettings()
                tunnelProvider.setLockDown(lockdownSettings).onFailure {
                    Timber.w(it, "Failed to restore lockdown/kill-switch on startup")
                }
            }
            else -> Unit
        }
    }
}
