package com.zaneschepke.wireguardautotunnel

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.StrictMode
import com.wgtunnel.backend.Backend
import com.wgtunnel.backend.BackendLog
import com.wgtunnel.backend.LogLevel
import com.wgtunnel.backend.service.AlwaysOnCallback
import com.wgtunnel.backend.service.RuntimeManager
import com.zaneschepke.logcatter.LogReader
import com.zaneschepke.wireguardautotunnel.core.event.TunnelEventDispatcher
import com.zaneschepke.wireguardautotunnel.core.orchestration.AppBoostrapCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.StartupCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.tunnel.AppProvider
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelOriginHolder
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.core.worker.UpdateCheckWorker
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
import com.zaneschepke.wireguardautotunnel.util.Constants
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

    private val startupCoordinator: StartupCoordinator by inject()

    private val tunnelCoordinator: TunnelCoordinator by inject()

    private val backend: Backend by inject()

    private val alwaysOnCallback =
        object : AlwaysOnCallback {
            override fun alwaysOnTriggered() {
                applicationScope.launch { startupCoordinator.handleAlwaysOnTrigger() }
            }

            override fun onStickyRestart() {
                applicationScope.launch { startupCoordinator.restoreAfterStickyRestart() }
            }

            override fun onVpnRevoked() {
                startupCoordinator.markVpnRevoked()
                applicationScope.launch { startupCoordinator.handleVpnRevoked() }
            }
        }

    @OptIn(KoinViewModelScopeApi::class)
    override fun onCreate() {
        super.onCreate()
        BackendLog.setMinLevel(if (BuildConfig.DEBUG) LogLevel.Debug else LogLevel.Info)
        startKoin {
            androidContext(this@WireGuardAutoTunnel)
            if (BuildConfig.DEBUG) androidLogger()
            workManagerFactory()
            modules(
                dispatchersModule,
                appModule,
                databaseModule,
                tunnelBackendProviderModule,
                workerModule,
                coordinatorModule,
            )
            options(viewModelScopeFactory())
            lazyModules(networkModule)
        }
        instance = this

        notificationService.createAllChannels()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
            )
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        } else {
            Timber.plant(ReleaseTree())
        }

        RuntimeManager.alwaysOnCallback = alwaysOnCallback

        val dispatcher = get<TunnelEventDispatcher>()
        val provider = get<TunnelProvider>()

        // for notifications
        dispatcher.bind(applicationScope, provider.events, tunnelCoordinator.errors)
        get<TunnelOriginHolder>().bind(applicationScope, tunnelCoordinator.actions)
        get<AppProvider>().bind(applicationScope)

        if (BuildConfig.FLAVOR == Constants.STANDALONE_FLAVOR) {
            UpdateCheckWorker.start(this)
        }

        applicationScope.launch(ioDispatcher) {
            boostrapCoordinator.bootstrap(this@WireGuardAutoTunnel)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // numeric compare also covers the deprecated levels above background on older apis
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            applicationScope.launch { get<LogReader>().clearBufferedLogs() }
        }
    }

    companion object {
        lateinit var instance: WireGuardAutoTunnel
            private set
    }
}
