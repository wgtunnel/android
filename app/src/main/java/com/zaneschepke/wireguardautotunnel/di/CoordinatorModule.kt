package com.zaneschepke.wireguardautotunnel.di

import com.zaneschepke.wireguardautotunnel.core.orchestration.AppBoostrapCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.AutoTunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.DnsSettingsCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.ShortcutCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.StartupCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelBackendCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val coordinatorModule = module {
    singleOf(::ShortcutCoordinator)
    singleOf(::TunnelBackendCoordinator)
    singleOf(::StartupCoordinator)
    singleOf(::AutoTunnelCoordinator)
    singleOf(::DnsSettingsCoordinator)
    single {
        TunnelCoordinator(
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
        )
    }
    singleOf(::AppBoostrapCoordinator)
}
