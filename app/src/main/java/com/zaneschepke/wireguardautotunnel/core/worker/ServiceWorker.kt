package com.zaneschepke.wireguardautotunnel.core.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zaneschepke.wireguardautotunnel.core.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import java.util.concurrent.TimeUnit
import timber.log.Timber

class ServiceWorker(
    context: Context,
    params: WorkerParameters,
    private val serviceManager: ServiceManager,
    private val autoTunnelSettingsRepository: AutoTunnelSettingsRepository,
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
        Timber.i("Service worker started")
        with(autoTunnelSettingsRepository.getAutoTunnelSettings()) {
            Timber.i("Checking to see if auto-tunnel has been killed by system")
            if (isAutoTunnelEnabled && serviceManager.autoTunnelService.value == null) {
                Timber.i("Service has been killed by system, restoring.")
                serviceManager.startAutoTunnelService()
            }
            return Result.success()
        }
    }
}
