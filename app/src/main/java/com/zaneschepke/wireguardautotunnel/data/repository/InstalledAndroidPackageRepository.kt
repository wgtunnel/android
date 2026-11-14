package com.zaneschepke.wireguardautotunnel.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.zaneschepke.wireguardautotunnel.domain.model.InstalledPackage
import com.zaneschepke.wireguardautotunnel.domain.repository.InstalledPackageRepository
import com.zaneschepke.wireguardautotunnel.util.extensions.getFriendlyAppName
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(FlowPreview::class)
class InstalledAndroidPackageRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : InstalledPackageRepository, AutoCloseable {

    // scoped to this instance so the receiver and cache are released with the owning scope
    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _installedPackages = MutableStateFlow<List<InstalledPackage>>(emptyList())
    override val installedPackages: StateFlow<List<InstalledPackage>> =
        _installedPackages.asStateFlow()

    init {
        // warm the cache
        repositoryScope.launch { refreshInstalledPackages() }

        // watch for packages changes and update the cache
        callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            trySend(intent?.action)
                        }
                    }

                val filter =
                    IntentFilter().apply {
                        addAction(Intent.ACTION_PACKAGE_ADDED)
                        addAction(Intent.ACTION_PACKAGE_REMOVED)
                        addAction(Intent.ACTION_PACKAGE_REPLACED)
                        addDataScheme("package")
                    }

                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )

                awaitClose { context.unregisterReceiver(receiver) }
            }
            .debounce(400.milliseconds)
            .onEach {
                Timber.d("Refreshing installed packages due to package change")
                refreshInstalledPackages()
            }
            .flowOn(ioDispatcher)
            .launchIn(repositoryScope)
    }

    override fun close() {
        repositoryScope.cancel()
    }

    override suspend fun getInstalledPackages(): List<InstalledPackage> =
        withContext(ioDispatcher) {
            _installedPackages.value.ifEmpty { refreshInstalledPackages() }
        }

    override suspend fun refreshInstalledPackages(): List<InstalledPackage> =
        withContext(ioDispatcher) {
            val pm = context.packageManager
            val packages =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(
                        PackageManager.PackageInfoFlags.of(0L),
                    )
                } else {
                    @Suppress("DEPRECATION") pm.getInstalledPackages(0)
                }
            // An app with no launcher icon on phone or TV is treated as a system app
            val launchable =
                launcherPackages(Intent.CATEGORY_LAUNCHER) +
                    launcherPackages(Intent.CATEGORY_LEANBACK_LAUNCHER)

            val installedPackages =
                packages
                    .filter { it.packageName != context.packageName }
                    .mapNotNull { packageInfo ->
                        try {
                            val appInfo =
                                packageInfo.applicationInfo
                                    ?: pm.getApplicationInfo(packageInfo.packageName, 0)

                            InstalledPackage(
                                name = pm.getFriendlyAppName(packageInfo.packageName, appInfo),
                                packageName = packageInfo.packageName,
                                uId = appInfo.uid,
                                isSystem = packageInfo.packageName !in launchable,
                            )
                        } catch (e: PackageManager.NameNotFoundException) {
                            Timber.e(e)
                            null
                        }
                    }

            _installedPackages.value = installedPackages
            installedPackages
        }

    private fun launcherPackages(category: String): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val activities =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION") context.packageManager.queryIntentActivities(intent, 0)
            }
        return activities.mapTo(HashSet()) { it.activityInfo.packageName }
    }
}
