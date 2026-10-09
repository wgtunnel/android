package com.zaneschepke.wireguardautotunnel.core.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.UpdateRepository
import com.zaneschepke.wireguardautotunnel.notification.NotificationService
import com.zaneschepke.wireguardautotunnel.util.Constants
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import timber.log.Timber

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
    private val updateRepository: UpdateRepository,
    private val notificationService: NotificationService,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (BuildConfig.FLAVOR != Constants.STANDALONE_FLAVOR || BuildConfig.DEBUG) {
            return Result.success()
        }

        Timber.i("UpdateCheckWorker: checking for updates (version=${BuildConfig.VERSION_NAME})")

        return updateRepository
            .checkForUpdate(BuildConfig.VERSION_NAME)
            .fold(
                onSuccess = { update ->
                    if (update != null) {
                        Timber.i("UpdateCheckWorker: update available ${update.version}")
                        notificationService.showUpdateAvailable(update.version)
                    } else {
                        Timber.i("UpdateCheckWorker: already up to date")
                    }
                    Result.success()
                },
                onFailure = { error ->
                    Timber.w(error, "UpdateCheckWorker: check failed")
                    Result.retry()
                },
            )
    }

    companion object {
        private const val TAG = "standalone_update_check"

        // Nightly workflow runs at 03:04 UTC so we'll check 1 hour later
        private const val TARGET_HOUR_UTC = 4
        private const val TARGET_MINUTE_UTC = 4

        fun start(context: Context) {
            if (BuildConfig.FLAVOR != Constants.STANDALONE_FLAVOR || BuildConfig.DEBUG) return

            val request =
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                        repeatInterval = 24,
                        repeatIntervalTimeUnit = TimeUnit.HOURS,
                    )
                    .setInitialDelay(millisUntilNextTargetUtc(), TimeUnit.MILLISECONDS)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .addTag(TAG)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    TAG,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            Timber.i(
                "UpdateCheckWorker scheduled; initial delay=${millisUntilNextTargetUtc()}ms to next ${TARGET_HOUR_UTC}:${TARGET_MINUTE_UTC.toString().padStart(2, '0')} UTC"
            )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }

        private fun millisUntilNextTargetUtc(): Long {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            var next =
                now.withHour(TARGET_HOUR_UTC)
                    .withMinute(TARGET_MINUTE_UTC)
                    .withSecond(0)
                    .withNano(0)
            if (!next.isAfter(now)) {
                next = next.plusDays(1)
            }
            return Duration.between(now, next)
                .toMillis()
                .coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
        }
    }
}
