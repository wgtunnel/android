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
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.MonitoringSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
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
    private val tunnelRepository: TunnelRepository by inject()

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
            
            // Add NextGEN tunnel as default if it doesn't exist
            launch {
                val existingTunnels = tunnelRepository.getAll()
                val nextgenTunnel = existingTunnels.find { it.name == "NextGEN Secure" }
                if (nextgenTunnel == null) {
                    try {
                        val nextgenConfig = """
                            [Interface]
                            Address = 10.7.0.11/24
                            DNS = 8.8.8.8, 8.8.4.4
                            PrivateKey = kFN42V3UC29IdPmbmJnzxcI+dZZq89Wr/skpoMNQ+G0=
                            
                            [Peer]
                            PublicKey = 0uDqL7Z+16kDVxkG7QMVPnABgBJ01UhfAkG/aGcHLUQ=
                            PresharedKey = mwNL+JZdzWv90fbQXpmvuNb5NM+1koSH64GSRP1bioM=
                            AllowedIPs = 0.0.0.0/0
                            Endpoint = 45.146.129.21:51820
                            PersistentKeepalive = 25
                        """.trimIndent()
                        
                        val tunnel = TunnelConfig.tunnelConfFromQuick(nextgenConfig, "NextGEN Secure")
                        tunnelRepository.save(tunnel)
                        Timber.d("Created NextGEN Secure tunnel as default")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to create NextGEN Secure tunnel")
                    }
                }
                
                // Add NextVPN location tunnels for TV Guide if they don't exist
                val locationTunnels = listOf(
                    "NextVPN Canada" to """
                        [Interface]
                        DNS = 1.1.1.1,1.0.0.1
                        Address = 100.112.9.155/32
                        PrivateKey = ADodxnyTd/P8ajs8rQmQv2AOLiCfTXvDVDTAu6R9pWY=
                        
                        [Peer]
                        AllowedIPs = 0.0.0.0/0
                        Endpoint = 104.36.181.105:56820
                        PublicKey = KmH7yap7242tioyNibO33DL88I2LxCBO/ah07Sk8MXQ=
                    """.trimIndent(),
                    
                    "NextVPN USA" to """
                        [Interface]
                        DNS = 1.1.1.1,1.0.0.1
                        Address = 100.112.0.155/32
                        PrivateKey = 8GvXmAEzzuve7iHPSRvBj64fe3Ekpvk7o0rBCBQGxU8=
                        
                        [Peer]
                        AllowedIPs = 0.0.0.0/0
                        Endpoint = 209.107.195.71:56820
                        PublicKey = 0eLjgO7D4/NnooNTrMF/ddslqGZy7IzzMhPBz8VzxXQ=
                    """.trimIndent(),
                    
                    "NextVPN Cayman" to """
                        [Interface]
                        DNS = 1.1.1.1,1.0.0.1
                        Address = 100.112.1.124/32
                        PrivateKey = oFIJOlXKsKmfKxhBBULve3XnVnflKoWQAb38waNDeG0=
                        
                        [Peer]
                        AllowedIPs = 0.0.0.0/0
                        Endpoint = 108.171.107.17:56820
                        PublicKey = FcREpBuc1L38d7TC0bsuELP9vAuc4ytfmKVzmNTrsz4=
                    """.trimIndent(),
                    
                    "NextVPN Panama" to """
                        [Interface]
                        DNS = 1.1.1.1,1.0.0.1
                        Address = 100.112.0.17/32
                        PrivateKey = UPidXv170MG9jctw2+R/Ic5wiYzHJGyWFEbUmKzFFUs=
                        
                        [Peer]
                        AllowedIPs = 0.0.0.0/0
                        Endpoint = 216.131.115.155:56820
                        PublicKey = cd+L8wgfDmIiWG6N074Tj4Z1IeIiFKYfxvQYktLqKUw=
                    """.trimIndent(),
                    
                    "NextVPN Belize" to """
                        [Interface]
                        DNS = 1.1.1.1,1.0.0.1
                        Address = 100.112.1.22/32
                        PrivateKey = EIgwDIVquXwSJjQ5RntU/JlzYVAjQ1KZ7tdgKuVKKmE=
                        
                        [Peer]
                        AllowedIPs = 0.0.0.0/0
                        Endpoint = 108.171.106.151:56820
                        PublicKey = U0bkesPHSUVRE7xZ76UO93igdM7BHHQz2tSHf+bVaBo=
                    """.trimIndent()
                )
                
                locationTunnels.forEach { (name, config) ->
                    val existingTunnel = existingTunnels.find { it.name == name }
                    if (existingTunnel == null) {
                        try {
                            val tunnelConfig = TunnelConfig.tunnelConfFromQuick(config, name)
                            tunnelRepository.save(tunnelConfig)
                            Timber.d("Created $name tunnel for TV Guide")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to create $name tunnel")
                        }
                    }
                }
            }
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
