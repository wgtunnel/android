package com.zaneschepke.wireguardautotunnel.data.repository

import android.os.Build
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.data.mapper.GitHubReleaseMapper
import com.zaneschepke.wireguardautotunnel.data.network.GitHubApi
import com.zaneschepke.wireguardautotunnel.domain.model.AppUpdate
import com.zaneschepke.wireguardautotunnel.domain.repository.UpdateRepository
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.NumberUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

class GitHubUpdateRepository(
    private val gitHubApi: GitHubApi,
    private val githubOwner: String,
    private val githubRepo: String,
    private val ioDispatcher: CoroutineDispatcher,
) : UpdateRepository {

    override suspend fun checkForUpdate(currentVersion: String): Result<AppUpdate?> =
        withContext(ioDispatcher) {
            Timber.i("Checking for update (current=$currentVersion)")
            val isNightly = BuildConfig.VERSION_NAME.contains("nightly", ignoreCase = true)
            val release =
                if (isNightly) {
                    gitHubApi.getNightlyRelease(githubOwner, githubRepo).onFailure(Timber::e)
                } else {
                    gitHubApi.getLatestRelease(githubOwner, githubRepo).onFailure(Timber::e)
                }
            release.map { release ->
                val choice =
                    ApkAssetSelector.select(
                        release.assets,
                        Constants.STANDALONE_FLAVOR,
                        Build.SUPPORTED_ABIS.toList(),
                    ) ?: return@map null
                val newVersion = choice.version

                Timber.i(
                    "Latest version: $newVersion (${choice.asset.name}), current version: $currentVersion"
                )
                val updateAvailable =
                    if (isNightly) {
                        newVersion != currentVersion
                    } else {
                        NumberUtils.compareVersions(newVersion, currentVersion) > 0
                    }

                if (updateAvailable) {
                    GitHubReleaseMapper.toAppUpdate(release, choice.asset, newVersion)
                } else {
                    null
                }
            }
        }
}
