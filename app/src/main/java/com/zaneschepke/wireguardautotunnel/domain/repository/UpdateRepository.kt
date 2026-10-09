package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.model.AppUpdate

interface UpdateRepository {
    suspend fun checkForUpdate(currentVersion: String): Result<AppUpdate?>
}
