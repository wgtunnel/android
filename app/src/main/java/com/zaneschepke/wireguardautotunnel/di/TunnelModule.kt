package com.zaneschepke.wireguardautotunnel.di

import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.android.util.RootShell
import com.wireguard.android.util.ToolsInstaller
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.core.tunnel.backend.KernelTunnel
import com.zaneschepke.wireguardautotunnel.core.tunnel.backend.RunConfigHelper
import com.zaneschepke.wireguardautotunnel.core.tunnel.backend.TunnelBackend
import com.zaneschepke.wireguardautotunnel.core.tunnel.backend.UserspaceTunnel
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.util.RootShellUtils
import com.zaneschepke.wireguardautotunnel.util.extensions.to
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import org.amnezia.awg.backend.Backend
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.ProxyGoBackend
import org.amnezia.awg.backend.RootTunnelActionHandler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val tunnelModule = module {
    single(named(Shell.TUNNEL)) { RootShell(androidContext()) }
    single(named(Shell.APP)) { RootShell(androidContext()) }

    single { RootShellUtils(get(named(Shell.APP)), get(named(Dispatcher.IO))) }

    singleOf(::RunConfigHelper)

    single<Backend>(named(Core.USERSPACE)) {
        GoBackend(
            androidContext(),
            RootTunnelActionHandler(org.amnezia.awg.util.RootShell(androidContext())),
        )
    }

    single<Backend>(named(Core.PROXY_USERSPACE)) {
        ProxyGoBackend(
            androidContext(),
            RootTunnelActionHandler(org.amnezia.awg.util.RootShell(androidContext())),
        )
    }

    single<com.wireguard.android.backend.Backend> {
        val shell = get<RootShell>(named(Shell.TUNNEL))
        WgQuickBackend(
                androidContext(),
                shell,
                ToolsInstaller(androidContext(), shell),
                com.wireguard.android.backend.RootTunnelActionHandler(shell),
            )
            .apply { setMultipleTunnels(true) }
    }

    single<TunnelBackend>(named(Core.KERNEL)) {
        KernelTunnel(get(), get<com.wireguard.android.backend.Backend>())
    }

    single<TunnelBackend>(qualifier = named(Core.USERSPACE)) {
        UserspaceTunnel(get<Backend>(named(Core.USERSPACE)), get())
    }

    single<TunnelBackend>(qualifier = named(Core.PROXY_USERSPACE)) {
        UserspaceTunnel(get<Backend>(named(Core.PROXY_USERSPACE)), get())
    }

    single<NetworkMonitor> {
        AndroidNetworkMonitor(
            androidContext(),
            object : AndroidNetworkMonitor.ConfigurationListener {
                override val detectionMethod =
                    get<AutoTunnelSettingsRepository>()
                        .flow
                        .distinctUntilChangedBy { it.wifiDetectionMethod }
                        .map { it.wifiDetectionMethod.to() }

                override val rootShell = get<RootShell>(named(Shell.APP))
            },
            get<CoroutineScope>(named(Scope.APPLICATION)),
        )
    }

    single {
        TunnelManager(
            get(named(Core.KERNEL)),
            get(named(Core.USERSPACE)),
            get(named(Core.PROXY_USERSPACE)),
            get(),
            get(),
            get(),
            get(),
            get(),
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
