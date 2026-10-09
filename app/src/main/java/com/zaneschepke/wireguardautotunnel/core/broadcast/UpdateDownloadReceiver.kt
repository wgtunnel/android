package com.zaneschepke.wireguardautotunnel.core.broadcast

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zaneschepke.wireguardautotunnel.MainActivity
import com.zaneschepke.wireguardautotunnel.di.Scope
import com.zaneschepke.wireguardautotunnel.domain.repository.UpdateDownloader
import com.zaneschepke.wireguardautotunnel.notification.NotificationService.Companion.EXTRA_SHOW_UPDATE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.qualifier.named

class UpdateDownloadReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> onDownloadComplete(intent)
            DownloadManager.ACTION_NOTIFICATION_CLICKED -> openSupportIfDownloading(context)
        }
    }

    private fun onDownloadComplete(intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId < 0) return

        // Resolved here on the main thread, it registers a lifecycle observer when first created
        val downloader: UpdateDownloader = get()
        val applicationScope: CoroutineScope = get(named(Scope.APPLICATION))

        val pending = goAsync()
        applicationScope.launch {
            try {
                downloader.onDownloadComplete(downloadId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun openSupportIfDownloading(context: Context) {
        val downloader: UpdateDownloader = get()
        if (downloader.activeUpdate() == null) return
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                putExtra(EXTRA_SHOW_UPDATE, true)
            }
        )
    }
}
