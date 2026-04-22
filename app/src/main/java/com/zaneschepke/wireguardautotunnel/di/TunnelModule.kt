package com.zaneschepke.wireguardautotunnel.di

import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.backend.RootShell
import com.zaneschepke.tunnel.util.RootShellException
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.util.extensions.to
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import timber.log.Timber

val tunnelModule = module {

    single {
        RootShell(androidContext())
    }

    single<Backend> {
        com.zaneschepke.tunnel.backend.TunnelBackend(androidContext())
    }

    single<NetworkMonitor> {
        AndroidNetworkMonitor(
            androidContext(),
            object : AndroidNetworkMonitor.ConfigurationListener {
                override fun runRootShellCommand(vararg cmd: String): String? {
                    val rootShell = get<RootShell>()
                    return try {
                        rootShell.start()
                        val result = rootShell.run(*cmd)
                        result.output
                    } catch (e : RootShellException) {
                        Timber.e(e)
                        null
                    } finally {
                        rootShell.stop()
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

    single {
        TunnelManager(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(named(Scope.APPLICATION)),
            get(named(Dispatcher.IO)),
        )
    }
}
