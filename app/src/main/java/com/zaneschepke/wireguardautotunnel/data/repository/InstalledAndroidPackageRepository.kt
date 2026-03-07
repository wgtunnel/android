package com.zaneschepke.wireguardautotunnel.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.zaneschepke.wireguardautotunnel.domain.model.InstalledPackage
import com.zaneschepke.wireguardautotunnel.domain.repository.InstalledPackageRepository
import com.zaneschepke.wireguardautotunnel.util.extensions.getFriendlyAppName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

class InstalledAndroidPackageRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : InstalledPackageRepository {

    private var cachedPackages: List<InstalledPackage>? = null

    override suspend fun getInstalledPackages(): List<InstalledPackage> =
        withContext(ioDispatcher) {
            cachedPackages?.let {
                return@withContext it
            }
            refreshInstalledPackages()
        }

    override suspend fun refreshInstalledPackages(): List<InstalledPackage> =
        withContext(ioDispatcher) {
            val packages = context.packageManager.getInstalledPackages(0)

            val installedPackages =
                packages.mapNotNull { packageInfo ->
                    try {
                        val appInfo =
                            context.packageManager.getApplicationInfo(packageInfo.packageName, 0)
                        InstalledPackage(
                            name =
                                context.packageManager.getFriendlyAppName(
                                    packageInfo.packageName,
                                    appInfo,
                                ),
                            packageName = packageInfo.packageName,
                            uId = appInfo.uid,
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        Timber.e(e)
                        null
                    }
                }

            cachedPackages = installedPackages

            installedPackages
        }
}
