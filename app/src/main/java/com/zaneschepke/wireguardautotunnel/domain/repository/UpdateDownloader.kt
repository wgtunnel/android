package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.model.AppUpdate
import com.zaneschepke.wireguardautotunnel.domain.model.UpdateDownloadState
import kotlinx.coroutines.flow.StateFlow

interface UpdateDownloader {
    val state: StateFlow<UpdateDownloadState>

    fun activeUpdate(): AppUpdate?

    suspend fun start(update: AppUpdate): Result<Unit>

    suspend fun cancel()

    suspend fun onDownloadComplete(downloadId: Long)
}
