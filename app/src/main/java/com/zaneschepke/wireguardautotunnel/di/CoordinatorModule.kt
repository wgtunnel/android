package com.zaneschepke.wireguardautotunnel.di

import com.zaneschepke.wireguardautotunnel.core.orchestration.AppBoostrapCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.AutoTunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.ShortcutCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.StartupCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelModeCoordinator
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val coordinatorModule = module {
    singleOf(::ShortcutCoordinator)
    singleOf(::TunnelModeCoordinator)
    singleOf(::StartupCoordinator)
    singleOf(::AutoTunnelCoordinator)
    single {
        TunnelCoordinator(
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
