package com.zaneschepke.wireguardautotunnel.domain.model

import java.io.File

enum class DownloadPause {
    WAITING_FOR_NETWORK,
    WAITING_FOR_WIFI,
    RETRYING,
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val pause: DownloadPause? = null,
    ) : UpdateDownloadState {
        val progress: Float?
            get() =
                if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                else null
    }

    data class Completed(val file: File) : UpdateDownloadState

    data object Failed : UpdateDownloadState
}
