package com.zaneschepke.wireguardautotunnel

import android.app.Application
import android.os.StrictMode
import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.di.tunnelModule
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.wireguardautotunnel.core.event.TunnelEventDispatcher
import com.zaneschepke.wireguardautotunnel.core.orchestration.AppBoostrapCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.di.Dispatcher
import com.zaneschepke.wireguardautotunnel.di.Scope
import com.zaneschepke.wireguardautotunnel.di.appModule
import com.zaneschepke.wireguardautotunnel.di.coordinatorModule
import com.zaneschepke.wireguardautotunnel.di.databaseModule
import com.zaneschepke.wireguardautotunnel.di.dispatchersModule
import com.zaneschepke.wireguardautotunnel.di.networkModule
import com.zaneschepke.wireguardautotunnel.di.tunnelBackendProviderModule
import com.zaneschepke.wireguardautotunnel.di.workerModule
import com.zaneschepke.wireguardautotunnel.notification.NotificationService
import com.zaneschepke.wireguardautotunnel.service.tile.AutoTunnelTileRefresher
import com.zaneschepke.wireguardautotunnel.service.tile.TunnelTileRefresher
import com.zaneschepke.wireguardautotunnel.util.ReleaseTree
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.annotation.KoinViewModelScopeApi
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.lazyModules
import org.koin.core.option.viewModelScopeFactory
import org.koin.core.qualifier.named
import timber.log.Timber

class WireGuardAutoTunnel : Application(), KoinComponent {

    private val applicationScope: CoroutineScope by inject(named(Scope.APPLICATION))
    private val ioDispatcher: CoroutineDispatcher by inject(named(Dispatcher.IO))

    private val boostrapCoordinator: AppBoostrapCoordinator by inject()

    private val notificationService: NotificationService by inject()

    private val tunnelCoordinator: TunnelCoordinator by inject()

    private val backend: Backend by inject()

    @OptIn(KoinViewModelScopeApi::class)
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@WireGuardAutoTunnel)
            if (BuildConfig.DEBUG) androidLogger()
            workManagerFactory()
            modules(
                dispatchersModule,
                appModule,
                databaseModule,
                tunnelBackendProviderModule,
                tunnelModule,
                workerModule,
                coordinatorModule,
            )
            options(viewModelScopeFactory())
            lazyModules(networkModule)
        }
        instance = this

        notificationService.createAllChannels()

        syncTiles()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
            )
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        } else {
            Timber.plant(ReleaseTree())
        }

        backend.setAlwaysOnCallback(
            object : VpnService.AlwaysOnCallback {
                override fun alwaysOnTriggered() {
                    applicationScope.launch { tunnelCoordinator.startDefault() }
                }
            }
        )

        val dispatcher = get<TunnelEventDispatcher>()
        val coordinator = get<TunnelCoordinator>()
        val provider = get<TunnelProvider>()

        // for notifications
        dispatcher.bind(
            applicationScope,
            provider.events,
            provider.backendStatus,
            coordinator.errors,
            tunnelCoordinator.tunnelDisplayStates,
        )

        applicationScope.launch(ioDispatcher) { boostrapCoordinator.bootstrap() }
    }

    private fun syncTiles() {
        AutoTunnelTileRefresher.refresh(this)
        TunnelTileRefresher.refresh(this)
    }

    companion object {
        lateinit var instance: WireGuardAutoTunnel
            private set
    }
}
