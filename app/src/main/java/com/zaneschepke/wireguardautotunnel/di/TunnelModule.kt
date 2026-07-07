package com.zaneschepke.wireguardautotunnel.di

import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.networkmonitor.StableNetworkEngine
import com.zaneschepke.tunnel.ApplicationProvider
import com.zaneschepke.tunnel.util.RootShell
import com.zaneschepke.tunnel.util.RootShellException
import com.zaneschepke.wireguardautotunnel.core.event.TunnelEventDispatcher
import com.zaneschepke.wireguardautotunnel.core.tunnel.AndroidApplicationProvider
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelBackendProvider
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.lifecyle.AppVisibilityObserver
import com.zaneschepke.wireguardautotunnel.notification.AndroidTunnelNotificationService
import com.zaneschepke.wireguardautotunnel.notification.TunnelNotificationService
import com.zaneschepke.wireguardautotunnel.util.extensions.to
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import timber.log.Timber

val tunnelBackendProviderModule = module {
    single<TunnelNotificationService> { AndroidTunnelNotificationService(get()) }
    single { AppVisibilityObserver() }
    singleOf(::TunnelEventDispatcher)

    single<ApplicationProvider> {
        AndroidApplicationProvider(
            notificationService = get(),
            tunnelNotificationService = get(),
            tunnelRepository = get(),
        )
    }

    single {
        StableNetworkEngine(
            get<CoroutineScope>(named(Scope.APPLICATION)),
            get<NetworkMonitor>().connectivityStateFlow,
        )
    }

    single<NetworkMonitor> {
        AndroidNetworkMonitor(
            androidContext(),
            object : AndroidNetworkMonitor.ConfigurationListener {
                override suspend fun runRootShellCommand(cmd: String): String? {
                    return try {
                        withTimeout(3_000.milliseconds) {
                            withContext(Dispatchers.IO) {
                                val result = RootShell.run(cmd)
                                result.output
                            }
                        }
                    } catch (e: RootShellException) {
                        Timber.e(e)
                        null
                    }
                }

                override val detectionMethod =
                    get<AutoTunnelSettingsRepository>()
                        .flow
                        .distinctUntilChangedBy { it.wifiDetectionMethod }
                        .map { it.wifiDetectionMethod.to() }
            },
            get<CoroutineScope>(named(Scope.APPLICATION)),
        )
    }

    single<TunnelProvider> {
        TunnelBackendProvider(get(), get(named(Scope.APPLICATION)), get(named(Dispatcher.IO)))
    }
}
