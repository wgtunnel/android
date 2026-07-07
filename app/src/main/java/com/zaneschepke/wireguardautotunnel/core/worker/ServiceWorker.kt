package com.zaneschepke.wireguardautotunnel.core.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.service.autotunnel.AutoTunnelStateHolder
import java.util.concurrent.TimeUnit
import timber.log.Timber

class ServiceWorker(
    context: Context,
    params: WorkerParameters,
    private val serviceManager: ServiceManager,
    private val autoTunnelSettingsRepository: AutoTunnelSettingsRepository,
    private val autoTunnelStateHolder: AutoTunnelStateHolder,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "auto_tunnel_service_monitor"

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }

        fun start(context: Context) {
            val periodicWorkRequest =
                PeriodicWorkRequestBuilder<ServiceWorker>(
                        repeatInterval = 15,
                        repeatIntervalTimeUnit = TimeUnit.MINUTES,
                    )
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWorkRequest,
                )
        }
    }

    override suspend fun doWork(): Result {
        Timber.i("AutoTunnel reconciliation worker running")

        val settings = autoTunnelSettingsRepository.getAutoTunnelSettings()

        if (!settings.isAutoTunnelEnabled) {
            return Result.success()
        }

        if (autoTunnelStateHolder.active.value) return Result.success()

        serviceManager.startAutoTunnelService()

        return Result.success()
    }
}
