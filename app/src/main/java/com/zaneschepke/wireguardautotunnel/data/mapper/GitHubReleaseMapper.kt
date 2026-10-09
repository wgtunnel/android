package com.zaneschepke.wireguardautotunnel.data.mapper

import com.zaneschepke.wireguardautotunnel.data.entity.Asset
import com.zaneschepke.wireguardautotunnel.data.entity.GitHubRelease
import com.zaneschepke.wireguardautotunnel.domain.model.AppUpdate

object GitHubReleaseMapper {
    fun toAppUpdate(
        gitHubRelease: GitHubRelease,
        apkAsset: Asset,
        newVersion: String,
    ): AppUpdate =
        AppUpdate(
            version = newVersion,
            releaseUrl = gitHubRelease.htmlUrl,
            apkUrl = apkAsset.browserDownloadUrl,
            apkFileName = apkAsset.name,
            apkSize = apkAsset.size.takeIf { it > 0 },
        )
}
