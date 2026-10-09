package com.zaneschepke.wireguardautotunnel.ui.screens.support.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.DownloadPause
import com.zaneschepke.wireguardautotunnel.domain.model.UpdateDownloadState
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import kotlin.math.roundToInt

@Composable
fun downloadStatusText(
    download: UpdateDownloadState.Downloading,
    includePercent: Boolean = false,
): String {
    val context = LocalContext.current
    return when (download.pause) {
        DownloadPause.WAITING_FOR_NETWORK -> stringResource(R.string.update_waiting_for_network)
        DownloadPause.WAITING_FOR_WIFI -> stringResource(R.string.update_waiting_for_wifi)
        DownloadPause.RETRYING -> stringResource(R.string.update_download_retrying)
        null -> {
            val downloaded = Formatter.formatShortFileSize(context, download.bytesDownloaded)
            val sizes =
                if (download.totalBytes > 0) {
                    stringResource(
                        R.string.update_download_sizes,
                        downloaded,
                        Formatter.formatShortFileSize(context, download.totalBytes),
                    )
                } else {
                    downloaded
                }
            val percent = download.progress?.takeIf { includePercent }
            if (percent != null) {
                stringResource(
                    R.string.update_download_percent,
                    (percent * 100).roundToInt(),
                    sizes,
                )
            } else {
                sizes
            }
        }
    }
}

@Composable
fun UpdateDownloadProgress(
    download: UpdateDownloadState.Downloading,
    modifier: Modifier = Modifier,
) {
    val progress = download.progress
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DescriptionText(downloadStatusText(download, includePercent = true))
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
