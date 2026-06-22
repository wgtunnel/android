package com.zaneschepke.tunnel.di

import com.zaneschepke.tunnel.TunnelLibraryInitializer
import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.backend.TunnelBackend
import com.zaneschepke.tunnel.backend.TunnelEngine
import com.zaneschepke.tunnel.backend.WireGuardTunnelEngine
import com.zaneschepke.tunnel.service.ServiceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val tunnelModule = module {
    single(createdAtStart = true) { TunnelLibraryInitializer.ensureLoaded(androidContext()) }

    single(named(CoroutineScopes.IO_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    single { ServiceHolder(androidContext()) }
    // expect networkMonitor and NotificationProvider to be available to koin from app
    single<Backend> { TunnelBackend(get(named(CoroutineScopes.IO_SCOPE)), get(), get()) }
    single<TunnelEngine> { WireGuardTunnelEngine(get()) }
}

enum class CoroutineScopes {
    IO_SCOPE
}
