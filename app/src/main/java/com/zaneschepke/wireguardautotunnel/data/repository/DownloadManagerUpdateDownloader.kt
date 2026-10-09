package com.zaneschepke.wireguardautotunnel.data.repository

import android.app.DownloadManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.data.network.StreamingFileDownloader
import com.zaneschepke.wireguardautotunnel.domain.model.AppUpdate
import com.zaneschepke.wireguardautotunnel.domain.model.DownloadPause
import com.zaneschepke.wireguardautotunnel.domain.model.UpdateDownloadState
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.UpdateDownloader
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.lifecyle.AppVisibilityObserver
import com.zaneschepke.wireguardautotunnel.notification.NotificationService
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The system DownloadManager owns the transfer, so it survives the UI and the process. The active
 * download id is persisted, the state here is only a view of it that the UI can observe. Falls back
 * to in app downloader if DownloadManager isn't available.
 */
class DownloadManagerUpdateDownloader(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val globalEffectRepository: GlobalEffectRepository,
    private val notificationService: NotificationService,
    private val appVisibilityObserver: AppVisibilityObserver,
    private val streamingDownloader: StreamingFileDownloader,
) : UpdateDownloader {

    private val downloadManager: DownloadManager? =
        context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutex = Mutex()
    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    override val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()
    private var pollJob: Job? = null

    private var fallbackJob: Job? = null
    private var fallbackUpdate: AppUpdate? = null

    init {
        // Pick up a download that was started before the process died
        scope.launch(ioDispatcher) { mutex.withLock { resume() } }
    }

    override suspend fun start(update: AppUpdate): Result<Unit> =
        withContext(ioDispatcher) {
            mutex.withLock {
                if (_state.value is UpdateDownloadState.Downloading) {
                    return@withLock Result.success(Unit)
                }
                val url = update.apkUrl
                val fileName = update.apkFileName
                if (url.isNullOrBlank() || fileName.isNullOrBlank()) {
                    return@withLock Result.failure(IOException("Update has no APK to download"))
                }
                discardPrevious()
                val viaManager = runCatching { enqueueWithManager(update, url, fileName) }
                if (viaManager.isSuccess) return@withLock viaManager

                // Also when the provider is missing, enqueue then throws instead of returning
                Timber.w(
                    viaManager.exceptionOrNull(),
                    "DownloadManager unusable, downloading in app",
                )
                prefs.edit { clear() }
                runCatching { startFallback(update, url, fileName) }
                    .onFailure {
                        Timber.e(it, "Failed to start update download")
                        _state.value = UpdateDownloadState.Failed
                    }
            }
        }

    private fun enqueueWithManager(update: AppUpdate, url: String, fileName: String) {
        val manager = downloadManager ?: throw IOException("DownloadManager is not available")
        val id = manager.enqueue(buildRequest(url, fileName, update.version))
        prefs.edit {
            putLong(KEY_ID, id)
                .putLong(KEY_SIZE, update.apkSize ?: NONE)
                .putString(KEY_FILE, fileName)
                .putString(KEY_VERSION, update.version)
                .putString(KEY_RELEASE_URL, update.releaseUrl)
                .putString(KEY_URL, url)
        }
        _state.value = UpdateDownloadState.Downloading(0L, update.apkSize ?: NONE)
        startPolling(id)
    }

    private fun startFallback(update: AppUpdate, url: String, fileName: String) {
        // The app's own files dir is covered by the FileProvider paths too
        val destination = File(context.getExternalFilesDir(null) ?: context.filesDir, fileName)
        val expected = update.apkSize ?: NONE
        fallbackUpdate = update
        _state.value = UpdateDownloadState.Downloading(0L, expected)
        fallbackJob =
            scope.launch(ioDispatcher) {
                val self = coroutineContext.job
                try {
                    streamingDownloader.download(url, destination, expected) { downloaded, total ->
                        val known = if (total > 0) total else expected
                        publishFallback(self, UpdateDownloadState.Downloading(downloaded, known))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Timber.e(t, "In app update download failed")
                    if (publishFallback(self, UpdateDownloadState.Failed) && !isUiListening()) {
                        notificationService.showUpdateDownloadFailed()
                    }
                    return@launch
                }
                if (publishFallback(self, UpdateDownloadState.Completed(destination))) {
                    deliver(destination)
                }
            }
    }

    // False when the download was canceled or replaced, so a late result can't overwrite the state
    private suspend fun publishFallback(self: Job, next: UpdateDownloadState): Boolean =
        mutex.withLock {
            if (fallbackJob !== self) return@withLock false
            if (next is UpdateDownloadState.Failed) fallbackUpdate = null
            _state.value = next
            true
        }

    override fun activeUpdate(): AppUpdate? = fallbackUpdate ?: storedUpdate()

    private fun storedUpdate(): AppUpdate? {
        if (prefs.getLong(KEY_ID, NONE) == NONE) return null
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        val fileName = prefs.getString(KEY_FILE, null) ?: return null
        return AppUpdate(
            version = version,
            releaseUrl = prefs.getString(KEY_RELEASE_URL, null),
            apkUrl = prefs.getString(KEY_URL, null),
            apkFileName = fileName,
            apkSize = prefs.getLong(KEY_SIZE, NONE).takeIf { it > 0 },
        )
    }

    override suspend fun cancel() {
        withContext(ioDispatcher) {
            mutex.withLock {
                pollJob?.cancel()
                pollJob = null
                // The running stream removes its own partial file when it stops
                fallbackJob?.cancel()
                fallbackJob = null
                fallbackUpdate = null
                val id = prefs.getLong(KEY_ID, NONE)
                if (id != NONE) runCatching { downloadManager?.remove(id) }
                prefs.edit { clear() }
                _state.value = UpdateDownloadState.Idle
            }
        }
    }

    override suspend fun onDownloadComplete(downloadId: Long) {
        var failed = false
        val finished =
            withContext(ioDispatcher) {
                mutex.withLock {
                    if (prefs.getLong(KEY_ID, NONE) != downloadId) return@withLock null
                    when (val result = query(downloadId)) {
                        is UpdateDownloadState.Completed -> {
                            pollJob?.cancel()
                            _state.value = result
                            // A repeated broadcast must not install or notify twice
                            if (prefs.getLong(KEY_DELIVERED, NONE) == downloadId) {
                                null
                            } else {
                                prefs.edit { putLong(KEY_DELIVERED, downloadId) }
                                result
                            }
                        }
                        is UpdateDownloadState.Failed -> {
                            pollJob?.cancel()
                            discardFailed(downloadId)
                            _state.value = result
                            failed = true
                            null
                        }
                        else -> null
                    }
                }
            }
        if (finished != null) deliver(finished.file)
        // The completion broadcast also fires for failures, and nothing else tells the user
        if (failed && !isUiListening()) notificationService.showUpdateDownloadFailed()
    }

    private suspend fun deliver(file: File) {
        if (isUiListening()) {
            globalEffectRepository.post(GlobalSideEffect.InstallApk(file))
        } else {
            notificationService.showUpdateReadyToInstall(file)
        }
    }

    private fun isUiListening() =
        appVisibilityObserver.isForeground.value && globalEffectRepository.hasSubscribers

    private fun resume() {
        val id = prefs.getLong(KEY_ID, NONE)
        if (id == NONE) return
        when (val current = query(id)) {
            null -> prefs.edit { clear() }
            is UpdateDownloadState.Downloading -> {
                _state.value = current
                startPolling(id)
            }
            is UpdateDownloadState.Completed ->
                if (prefs.getString(KEY_VERSION, null) == BuildConfig.VERSION_NAME) {
                    // Installed since, the process restarts with the new version
                    runCatching { downloadManager?.remove(id) }
                    prefs.edit { clear() }
                    apkFiles().forEach { it.delete() }
                } else {
                    _state.value = current
                }
            is UpdateDownloadState.Failed -> discardFailed(id)
            UpdateDownloadState.Idle -> Unit
        }
    }

    // Progress for the UI only
    private fun startPolling(id: Long) {
        pollJob?.cancel()
        pollJob =
            scope.launch(ioDispatcher) {
                while (isActive) {
                    val next = query(id)
                    val keepPolling = mutex.withLock {
                        // Canceled or replaced while we were querying
                        if (prefs.getLong(KEY_ID, NONE) != id) return@withLock false
                        when (next) {
                            null -> {
                                // Removed outside the app
                                prefs.edit { clear() }
                                _state.value = UpdateDownloadState.Idle
                                false
                            }
                            is UpdateDownloadState.Downloading -> {
                                _state.value = next
                                true
                            }
                            is UpdateDownloadState.Completed -> {
                                _state.value = next
                                false
                            }
                            is UpdateDownloadState.Failed -> {
                                discardFailed(id)
                                _state.value = next
                                false
                            }
                            UpdateDownloadState.Idle -> false
                        }
                    }
                    if (!keepPolling) return@launch
                    delay(
                        (if (_state.subscriptionCount.value > 0) FAST_POLL_MS else SLOW_POLL_MS)
                            .milliseconds
                    )
                }
            }
    }

    private fun query(id: Long): UpdateDownloadState? {
        val manager = downloadManager ?: return null
        val cursor =
            runCatching { manager.query(DownloadManager.Query().setFilterById(id)) }.getOrNull()
                ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded =
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val reportedTotal =
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val expected = prefs.getLong(KEY_SIZE, NONE)
            val total = if (reportedTotal > 0) reportedTotal else expected

            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL ->
                    completedState(
                        c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)),
                        expected,
                    )
                DownloadManager.STATUS_FAILED -> {
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    Timber.w("Update download failed, DownloadManager reason=$reason")
                    UpdateDownloadState.Failed
                }
                DownloadManager.STATUS_PAUSED -> {
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    UpdateDownloadState.Downloading(downloaded, total, pauseOf(reason))
                }
                else -> UpdateDownloadState.Downloading(downloaded, total)
            }
        }
    }

    private fun pauseOf(reason: Int): DownloadPause =
        when (reason) {
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> DownloadPause.WAITING_FOR_NETWORK
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> DownloadPause.WAITING_FOR_WIFI
            else -> DownloadPause.RETRYING
        }

    private fun completedState(localUri: String?, expectedSize: Long): UpdateDownloadState {
        val file =
            localUri?.toUri()?.path?.let(::File)?.takeIf { it.exists() }
                ?: prefs
                    .getString(KEY_FILE, null)
                    ?.let { File(downloadDir(), it) }
                    ?.takeIf { it.exists() }
        if (file == null || file.length() <= 0L) return UpdateDownloadState.Failed
        if (expectedSize > 0 && file.length() != expectedSize) {
            Timber.w("Downloaded APK is ${file.length()} bytes, expected $expectedSize")
            file.delete()
            return UpdateDownloadState.Failed
        }
        return UpdateDownloadState.Completed(file)
    }

    private fun buildRequest(url: String, fileName: String, version: String) =
        DownloadManager.Request(url.toUri()).apply {
            setTitle(context.getString(R.string.app_name))
            setDescription(version)
            setMimeType(APK_MIME_TYPE)
            setDestinationInExternalFilesDir(context, null, fileName)
            // Our own notification takes over on completion, unless the user can't see it
            setNotificationVisibility(
                if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    DownloadManager.Request.VISIBILITY_VISIBLE
                } else {
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                }
            )
            addRequestHeader("User-Agent", "wgtunnel/${BuildConfig.VERSION_NAME} (Android)")
        }

    // Only for a download that is finished with, so the next one starts clean
    private fun discardPrevious() {
        val old = prefs.getLong(KEY_ID, NONE)
        if (old != NONE) runCatching { downloadManager?.remove(old) }
        prefs.edit { clear() }
        fallbackJob?.cancel()
        fallbackJob = null
        fallbackUpdate = null
        apkFiles().forEach { it.delete() }
    }

    private fun discardFailed(id: Long) {
        runCatching { downloadManager?.remove(id) }
        prefs.edit { clear() }
    }

    // Downloads of either kind, including a partial one left by an in-app stream
    private fun apkFiles(): List<File> =
        listOfNotNull(downloadDir(), context.filesDir)
            .flatMap { it.listFiles().orEmpty().asList() }
            .filter {
                it.extension.equals("apk", ignoreCase = true) ||
                    it.name.endsWith(
                        ".apk${StreamingFileDownloader.PART_SUFFIX}",
                        ignoreCase = true,
                    )
            }

    private fun downloadDir(): File? = context.getExternalFilesDir(null)

    private companion object {
        const val PREFS_NAME = "app_update_download"
        const val KEY_ID = "download_id"
        const val KEY_SIZE = "expected_size"
        const val KEY_FILE = "file_name"
        const val KEY_VERSION = "version"
        const val KEY_RELEASE_URL = "release_url"
        const val KEY_URL = "url"
        const val KEY_DELIVERED = "delivered_id"
        const val NONE = -1L
        const val FAST_POLL_MS = 500L
        const val SLOW_POLL_MS = 5_000L
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
