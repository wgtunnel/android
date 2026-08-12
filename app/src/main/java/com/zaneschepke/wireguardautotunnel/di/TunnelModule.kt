package com.zaneschepke.wireguardautotunnel.di

import com.wgtunnel.backend.ApplicationProvider
import com.wgtunnel.backend.Backend
import com.wgtunnel.backend.TunnelBackend
import com.wgtunnel.backend.exception.ShellException
import com.wgtunnel.backend.shell.ShellExecutor
import com.wgtunnel.backend.system.NetworkSnapshot
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.networkmonitor.StableNetworkEngine
import com.zaneschepke.wireguardautotunnel.core.event.TunnelEventDispatcher
import com.zaneschepke.wireguardautotunnel.core.tunnel.AppProvider
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        AppProvider(
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
                                val result = ShellExecutor().run(cmd)
                                result.output
                            }
                        }
                    } catch (e: ShellException) {
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

    single<Backend> {
        val scope = get<CoroutineScope>(named(Scope.APPLICATION))
        val androidMonitor = get<com.zaneschepke.networkmonitor.NetworkMonitor>()

        val mappedNetworkState: StateFlow<NetworkSnapshot?> =
            androidMonitor.connectivityStateFlow
                .filterNotNull()
                .map { state ->
                    NetworkSnapshot(
                        key = state.activeNetwork.key(),
                        hasIpv6 = state.hasIpv6,
                        isUsable = state.hasUsableNetwork(),
                        network = state.activeNetwork.network,
                    )
                }
                .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

        val networkMonitor =
            object : com.wgtunnel.backend.system.NetworkMonitor {
                override val networkState: StateFlow<NetworkSnapshot?> = mappedNetworkState
            }
        TunnelBackend(scope, get(), networkMonitor)
    }

    single<TunnelProvider> {
        TunnelBackendProvider(
            get<Backend>(),
            get(named(Scope.APPLICATION)),
            get(named(Dispatcher.IO)),
        )
    }
}
