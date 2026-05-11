package com.zaneschepke.wireguardautotunnel.core.service.autotunnel

import android.content.Intent
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.networkmonitor.StableNetworkEngine
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.notification.AndroidNotificationService
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationService
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelProvider
import com.zaneschepke.wireguardautotunnel.data.model.TunnelMode
import com.zaneschepke.wireguardautotunnel.di.Dispatcher
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
import com.zaneschepke.wireguardautotunnel.domain.events.AutoTunnelEvent
import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.state.AutoTunnelState
import com.zaneschepke.wireguardautotunnel.domain.state.toDomain
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.extensions.to
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import timber.log.Timber

class AutoTunnelService : LifecycleService() {

    private val engine = AutoTunnelEngine()

    private val networkEngine : StableNetworkEngine by inject()

    private val notificationService: NotificationService by inject()

    private val ioDispatcher: CoroutineDispatcher by inject(named(Dispatcher.IO))

    private val stateHolder: AutoTunnelStateHolder by inject()

    private val tunnelProvider: TunnelProvider by inject()

    private val autoTunnelRepository: AutoTunnelSettingsRepository by inject()
    private val settingsRepository: GeneralSettingRepository by inject()
    private val tunnelsRepository: TunnelRepository by inject()
    private val tunnelCoordinator: TunnelCoordinator by inject()
    private var autoTunnelJob: Job? = null
    private var permissionsJob: Job? = null

    private data class PermissionWarningState(
        val detectionMethod: AndroidNetworkMonitor.WifiDetectionMethod,
        val locationServicesEnabled: Boolean,
        val locationPermissionsEnabled: Boolean,
        val ssidReadRequired: Boolean,
    )

    private val autoTunnelStateFlow: Flow<AutoTunnelState> by lazy {
        val networkFlow = networkEngine.stableState.mapNotNull { it?.state?.toDomain() }

        val settingsFlow =
            combineSettings()

        val backendFlow =
            tunnelProvider.backendStatus

        combine(networkFlow, settingsFlow, backendFlow) { network, settings, backend ->

            AutoTunnelState(
                networkState = network,
                settings = settings.second,
                tunnelMode = settings.first,
                tunnels = settings.third,
                backendStatus = backend
            )
        }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)
    }

    override fun onCreate() {
        super.onCreate()
        stateHolder.setActive(true)
        launchWatcherNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Timber.d("onStartCommand executed with startId: $startId")
        start()
        return START_STICKY
    }

    fun start() {
        stateHolder.setActive(true)
        launchWatcherNotification()
        autoTunnelJob?.cancel()
        autoTunnelJob = startAutoTunnelStateJob()
        permissionsJob?.cancel()
        permissionsJob = startLocationPermissionsNotificationJob()
    }

    fun stop() {
        stateHolder.setActive(false)
        stopSelf()
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stateHolder.setActive(false)
        super.onDestroy()
    }

    private fun launchWatcherNotification(
        description: String = getString(R.string.monitoring_state_changes)
    ) {
        val notification =
            notificationService.createNotification(
                AndroidNotificationService.NotificationChannels.AUTO_TUNNEL,
                title = getString(R.string.auto_tunnel_title),
                description = description,
                actions =
                    listOf(
                        notificationService.createNotificationAction(
                            NotificationAction.AUTO_TUNNEL_OFF
                        )
                    ),
                onGoing = true,
                groupKey = NotificationService.AUTO_TUNNEL_GROUP_KEY,
                isGroupSummary = true,
            )
        ServiceCompat.startForeground(
            this,
            NotificationService.AUTO_TUNNEL_NOTIFICATION_ID,
            notification,
            Constants.SPECIAL_USE_SERVICE_TYPE_ID,
        )
    }



    private fun startAutoTunnelStateJob(): Job =
        lifecycleScope.launch(ioDispatcher) {
            autoTunnelStateFlow.collect { state ->
                val event = engine.evaluate(state)
                handleAutoTunnelEvent(event)
            }
        }

    private fun combineSettings():
        Flow<Triple<TunnelMode, AutoTunnelSettings, List<TunnelConfig>>> {
        return combine(
                settingsRepository.flow.map { it.tunnelMode }.distinctUntilChanged(),
                autoTunnelRepository.flow,
                tunnelsRepository.userTunnelsFlow,
            ) { appMode, autoTunnel, tunnels ->
                Triple(appMode, autoTunnel, tunnels)
            }
            .distinctUntilChanged()
    }

    private fun startLocationPermissionsNotificationJob(): Job =
        lifecycleScope.launch(ioDispatcher) {
            autoTunnelStateFlow
                .map { state ->
                    PermissionWarningState(
                        detectionMethod = state.settings.wifiDetectionMethod.to(),
                        locationServicesEnabled = state.networkState.locationServicesEnabled,
                        locationPermissionsEnabled = state.networkState.locationPermissionGranted,
                        ssidReadRequired =
                            state.tunnels.any { it.tunnelNetworks.isNotEmpty() } ||
                                    state.settings.trustedNetworkSSIDs.isNotEmpty()
                    )
                }
                .distinctUntilChanged()
                .collect { state ->

                    val wifiMode = state.detectionMethod

                    if (
                        wifiMode == AndroidNetworkMonitor.WifiDetectionMethod.DEFAULT ||
                        wifiMode == AndroidNetworkMonitor.WifiDetectionMethod.LEGACY
                    ) {

                        if (!state.ssidReadRequired) {
                            notificationService.remove(NotificationService.AUTO_TUNNEL_LOCATION_SERVICES_ID)
                            notificationService.remove(NotificationService.AUTO_TUNNEL_LOCATION_PERMISSION_ID)
                            return@collect
                        }

                        if (!state.locationPermissionsEnabled) {
                            val notification = notificationService.createNotification(
                                AndroidNotificationService.NotificationChannels.AUTO_TUNNEL,
                                title = getString(R.string.warning),
                                description = getString(R.string.location_permissions_missing),
                            )

                            notificationService.show(
                                NotificationService.AUTO_TUNNEL_LOCATION_PERMISSION_ID,
                                notification,
                            )
                        } else {
                            notificationService.remove(
                                NotificationService.AUTO_TUNNEL_LOCATION_PERMISSION_ID
                            )
                        }

                        if (!state.locationServicesEnabled) {
                            val notification = notificationService.createNotification(
                                AndroidNotificationService.NotificationChannels.AUTO_TUNNEL,
                                title = getString(R.string.warning),
                                description = getString(R.string.location_services_not_detected),
                            )

                            notificationService.show(
                                NotificationService.AUTO_TUNNEL_LOCATION_SERVICES_ID,
                                notification,
                            )
                        } else {
                            notificationService.remove(
                                NotificationService.AUTO_TUNNEL_LOCATION_SERVICES_ID
                            )
                        }
                    }
                }
        }

    private suspend fun handleAutoTunnelEvent(event: AutoTunnelEvent) {
        when (event) {
            is AutoTunnelEvent.Start ->
                event.tunnelConfig?.let { config ->
                    tunnelCoordinator.startTunnel(config)
                } ?: Timber.w("Received auto-tunnel start event without config...")
            is AutoTunnelEvent.Stop ->
                tunnelProvider.stopActiveTunnels()
            AutoTunnelEvent.DoNothing -> Unit
        }
}
}
