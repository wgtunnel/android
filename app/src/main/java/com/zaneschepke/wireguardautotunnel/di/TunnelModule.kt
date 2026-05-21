package com.zaneschepke.wireguardautotunnel.di

import android.app.Notification
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.networkmonitor.StableNetworkEngine
import com.zaneschepke.tunnel.NotificationProvider
import com.zaneschepke.tunnel.backend.RootShell
import com.zaneschepke.tunnel.util.RootShellException
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.event.TunnelEventDispatcher
import com.zaneschepke.wireguardautotunnel.core.notification.AndroidNotificationService.NotificationChannels
import com.zaneschepke.wireguardautotunnel.core.notification.AndroidTunnelNotificationService
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.PROXY_GROUP_KEY
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService.Companion.VPN_GROUP_KEY
import com.zaneschepke.wireguardautotunnel.core.notification.TunnelNotificationService
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelBackendProvider
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.util.extensions.to
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
    single<TunnelNotificationService> { AndroidTunnelNotificationService(get(), get()) }
    singleOf(::TunnelEventDispatcher)

    single<NotificationProvider> {
        val notificationService = get<NotificationService>()
        val context = androidContext()
        object : NotificationProvider {
            override val vpnInitNotification: Notification
                get() =
                    notificationService.createNotification(
                        channel = NotificationChannels.VPN,
                        title = context.getString(R.string.initializing),
                        onGoing = true,
                        groupKey = VPN_GROUP_KEY,
                    )

            override val proxyInitNotification: Notification
                get() =
                    notificationService.createNotification(
                        channel = NotificationChannels.PROXY,
                        title = context.getString(R.string.initializing),
                        onGoing = true,
                        groupKey = PROXY_GROUP_KEY,
                    )

            override val vpnNotificationId: Int
                get() = NotificationService.VPN_NOTIFICATION_ID

            override val proxyNotificationId: Int
                get() = NotificationService.PROXY_NOTIFICATION_ID
        }
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
                        withTimeout(3_000) {
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
