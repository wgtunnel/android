package com.zaneschepke.wireguardautotunnel.domain.repository

import com.zaneschepke.wireguardautotunnel.domain.model.InstalledPackage
import kotlinx.coroutines.flow.StateFlow

interface InstalledPackageRepository {
    val installedPackages: StateFlow<List<InstalledPackage>>

    suspend fun getInstalledPackages(): List<InstalledPackage>

    suspend fun refreshInstalledPackages(): List<InstalledPackage>
}
