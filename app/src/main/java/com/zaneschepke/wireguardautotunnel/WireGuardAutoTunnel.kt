package com.zaneschepke.wireguardautotunnel

import android.app.Application
import android.os.StrictMode
import com.zaneschepke.logcatter.LogReader
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationMonitor
import com.zaneschepke.wireguardautotunnel.di.Dispatcher
import com.zaneschepke.wireguardautotunnel.di.Scope
import com.zaneschepke.wireguardautotunnel.di.appModule
import com.zaneschepke.wireguardautotunnel.di.databaseModule
import com.zaneschepke.wireguardautotunnel.di.dispatchersModule
import com.zaneschepke.wireguardautotunnel.di.networkModule
import com.zaneschepke.wireguardautotunnel.di.tunnelModule
import com.zaneschepke.wireguardautotunnel.di.workerModule
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.util.ReleaseTree
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.lazyModules
import org.koin.core.option.viewModelScopeFactory
import org.koin.core.qualifier.named
import timber.log.Timber

class WireGuardAutoTunnel : Application(), KoinComponent {

    private val applicationScope: CoroutineScope by inject(named(Scope.APPLICATION))
    private val ioDispatcher: CoroutineDispatcher by inject(named(Dispatcher.IO))
    private val logReader: LogReader by inject()

    private val monitoringRepository: MonitoringSettingsRepository by inject()
    private val notificationMonitor: NotificationMonitor by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@WireGuardAutoTunnel)
            if (BuildConfig.DEBUG) androidLogger()
            workManagerFactory()
            modules(dispatchersModule, appModule, databaseModule, tunnelModule, workerModule)
            options(viewModelScopeFactory())
            lazyModules(networkModule)
        }
        instance = this
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .build()
            )
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        } else {
            Timber.plant(ReleaseTree())
        }

        applicationScope.launch(ioDispatcher) {
            launch {
                monitoringRepository.flow
                    .distinctUntilChangedBy { it.isLocalLogsEnabled }
                    .collect { settings ->
                        if (settings.isLocalLogsEnabled) {
                            logReader.start()
                        } else {
                            logReader.stop()
                        }
                    }
            }
            launch { notificationMonitor.handleApplicationNotifications() }
        }
    }

    companion object {
        private val _uiActive = MutableStateFlow(false)

        val uiActive: StateFlow<Boolean>
            get() = _uiActive

        fun setUiActive(active: Boolean) {
            _uiActive.update { active }
        }

        @Volatile private var lastActiveTunnels: List<Int> = emptyList()

        @Synchronized
        fun getLastActiveTunnels(): List<Int> {
            return lastActiveTunnels
        }

        @Synchronized
        fun setLastActiveTunnels(newTunnels: List<Int>) {
            lastActiveTunnels = newTunnels
        }

        lateinit var instance: WireGuardAutoTunnel
            private set
    }
}
