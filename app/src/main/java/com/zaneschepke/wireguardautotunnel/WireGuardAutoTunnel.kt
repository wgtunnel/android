package com.zaneschepke.wireguardautotunnel

import android.app.Application
import android.os.StrictMode
import com.zaneschepke.logcatter.LogReader
import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.di.tunnelModule
import com.zaneschepke.tunnel.model.DnsBoostrapConfig.*
import com.zaneschepke.tunnel.model.DnsBoostrapMode.Custom
import com.zaneschepke.tunnel.model.DnsBoostrapMode.System
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.wireguardautotunnel.core.event.TunnelEventDispatcher
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.data.model.DnsProtocol
import com.zaneschepke.wireguardautotunnel.di.*
import com.zaneschepke.wireguardautotunnel.domain.repository.DnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.util.ReleaseTree
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
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
    private val logReader: LogReader by inject()

    private val monitoringRepository: MonitoringSettingsRepository by inject()
    private val dnsSettingRepository: DnsSettingsRepository by inject()

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

        backend.setAlwaysOnCallback(
            object : VpnService.AlwaysOnCallback {
                override fun alwaysOnTriggered() {
                    applicationScope.launch {
                        tunnelCoordinator.startDefault()
                    }
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
        )

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
            // boostrap DNS setting
            launch {
                val dnsSettings = dnsSettingRepository.getDnsSettings()
                val dnsBoostrapMode =
                    when (dnsSettings.dnsProtocol) {
                        DnsProtocol.SYSTEM -> System
                        DnsProtocol.DOH -> Custom(DoH(dnsSettings.dnsEndpoint))

                        DnsProtocol.DOT -> Custom(DoT(dnsSettings.dnsEndpoint))
                        DnsProtocol.UDP -> Custom(Plain(dnsSettings.dnsEndpoint))
                    }
                backend.setBootstrapDnsMode(dnsBoostrapMode)
            }
        }
    }

    companion object {
        lateinit var instance: WireGuardAutoTunnel
            private set
    }
}
