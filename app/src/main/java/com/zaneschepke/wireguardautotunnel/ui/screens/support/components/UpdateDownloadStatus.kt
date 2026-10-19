package com.zaneschepke.wireguardautotunnel.ui.screens.support.components

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.DownloadPause
import com.zaneschepke.wireguardautotunnel.domain.model.UPDATE_PROGRESS_SETTLE_MS
import com.zaneschepke.wireguardautotunnel.domain.model.UpdateDownloadState
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class UpdateDownloadPresentation(
    val showProgress: Boolean,
    val indeterminate: Boolean,
    val fraction: Float,
    val labelBytes: Long,
    val labelTotal: Long,
    val pause: DownloadPause?,
) {
    fun asDownloading(): UpdateDownloadState.Downloading =
        UpdateDownloadState.Downloading(labelBytes, labelTotal, pause)
}

/** Remembers that this composable saw an in-progress download, and the last known size. */
private class DownloadLatch {
    var sawDownloading: Boolean = false
    var latchedTotal: Long = 0L
}

@Composable
fun rememberUpdateDownloadPresentation(download: UpdateDownloadState): UpdateDownloadPresentation {
    val anim = remember { Animatable(0f) }
    val latch = remember { DownloadLatch() }
    var settled by remember { mutableStateOf(false) }
    // Written here so the first Completed frame still knows a download was on screen. An effect
    // would run a frame late and flash the install action.
    when (download) {
        is UpdateDownloadState.Downloading -> {
            latch.sawDownloading = true
            if (download.totalBytes > 0) latch.latchedTotal = download.totalBytes
        }
        UpdateDownloadState.Idle,
        UpdateDownloadState.Failed -> {
            latch.sawDownloading = false
            latch.latchedTotal = 0L
        }
        is UpdateDownloadState.Completed -> Unit
    }

    LaunchedEffect(download) {
        when (download) {
            is UpdateDownloadState.Downloading -> {
                settled = false
                if (download.pause != null) return@LaunchedEffect
                val target = download.progress
                if (target == null || download.bytesDownloaded <= 0L) {
                    if (anim.value != 0f) anim.snapTo(0f)
                    return@LaunchedEffect
                }
                // A new download starts at 0. Animating backward would drain a full bar.
                if (target < anim.value) anim.snapTo(target)
                anim.animateTo(target, settleTween())
            }
            is UpdateDownloadState.Completed -> {
                if (!latch.sawDownloading) return@LaunchedEffect
                if (anim.value < 1f) anim.animateTo(1f, settleTween())
                settled = true
            }
            UpdateDownloadState.Idle,
            UpdateDownloadState.Failed -> {
                settled = false
                if (anim.value != 0f) anim.snapTo(0f)
            }
        }
    }

    val animated = anim.value
    val pause = (download as? UpdateDownloadState.Downloading)?.pause
    val indeterminate =
        download is UpdateDownloadState.Downloading &&
            pause == null &&
            (download.bytesDownloaded <= 0L || download.progress == null)
    val target =
        (download as? UpdateDownloadState.Downloading)?.takeIf { it.pause == null }?.progress
    val fraction =
        when {
            indeterminate -> 0f
            target != null && target < animated -> target
            else -> animated
        }
    val labelTotal =
        when (download) {
            is UpdateDownloadState.Downloading ->
                download.totalBytes.takeIf { it > 0 } ?: latch.latchedTotal
            else -> latch.latchedTotal
        }
    val labelBytes =
        if (labelTotal > 0) {
            (fraction * labelTotal).roundToLong().coerceIn(0L, labelTotal)
        } else if (download is UpdateDownloadState.Downloading) {
            download.bytesDownloaded
        } else {
            0L
        }
    val showProgress =
        when (download) {
            is UpdateDownloadState.Downloading -> true
            is UpdateDownloadState.Completed -> latch.sawDownloading && !settled
            else -> false
        }
    return UpdateDownloadPresentation(
        showProgress = showProgress,
        indeterminate = indeterminate,
        fraction = fraction,
        labelBytes = labelBytes,
        labelTotal = labelTotal,
        pause = pause,
    )
}

private fun settleTween() =
    tween<Float>(durationMillis = UPDATE_PROGRESS_SETTLE_MS, easing = LinearEasing)

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
    presentation: UpdateDownloadPresentation,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DescriptionText(
            downloadStatusText(
                presentation.asDownloading(),
                includePercent = !presentation.indeterminate,
            )
        )
        if (presentation.indeterminate) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { presentation.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
