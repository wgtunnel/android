package com.zaneschepke.wireguardautotunnel.service.autotunnel

import android.content.Intent
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.networkmonitor.StableNetworkEngine
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.di.Dispatcher
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.events.AutoTunnelEvent
import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.state.AutoTunnelState
import com.zaneschepke.wireguardautotunnel.domain.state.toDomain
import com.zaneschepke.wireguardautotunnel.notification.AndroidNotificationService
import com.zaneschepke.wireguardautotunnel.notification.NotificationService
import com.zaneschepke.wireguardautotunnel.service.tile.AutoTunnelTileRefresher
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.extensions.to
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import timber.log.Timber

class AutoTunnelService : LifecycleService() {

    private val engine = AutoTunnelEngine()

    private val reconciliationMutex = Mutex()

    private val networkEngine: StableNetworkEngine by inject()

    private val notificationService: NotificationService by inject()

    private val ioDispatcher: CoroutineDispatcher by inject(named(Dispatcher.IO))

    private val stateHolder: AutoTunnelStateHolder by inject()

    private val autoTunnelRepository: AutoTunnelSettingsRepository by inject()
    private val settingsRepository: GeneralSettingRepository by inject()
    private val tunnelsRepository: TunnelRepository by inject()
    private val tunnelCoordinator: TunnelCoordinator by inject()
    private var autoTunnelJob: Job? = null
    private var permissionsJob: Job? = null
    private var overridesJob: Job? = null
    private var noInternetStopJob: Job? = null

    private data class PermissionWarningState(
        val detectionMethod: AndroidNetworkMonitor.WifiDetectionMethod,
        val locationServicesEnabled: Boolean,
        val locationPermissionsEnabled: Boolean,
        val ssidReadRequired: Boolean,
    )

    @Volatile private var hasUserOverride = false
    private var lastNetworkFingerprint: AutoTunnelState.NetworkFingerprint? = null

    @OptIn(FlowPreview::class)
    private val autoTunnelStateFlow: Flow<AutoTunnelState> by lazy {
        val networkFlow = networkEngine.stableState.mapNotNull { it?.state?.toDomain() }

        val settingsFlow = combineSettings()

        val backendFlow =
            tunnelCoordinator.backendStatus
                .distinctUntilChanged { old, new -> old.activeTunnels == new.activeTunnels }
                .debounce(300L.milliseconds)

        combine(networkFlow, settingsFlow, backendFlow) { network, settings, backend ->
                AutoTunnelState(
                    networkState = network,
                    settings = settings.second,
                    tunnelMode = settings.first,
                    tunnels = settings.third,
                    backendStatus = backend,
                )
            }
            .distinctUntilChanged()
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
        AutoTunnelTileRefresher.refresh(this)
        launchWatcherNotification()
        autoTunnelJob?.cancel()
        autoTunnelJob = startAutoTunnelStateJob()
        permissionsJob?.cancel()
        permissionsJob = startLocationPermissionsNotificationJob()
        overridesJob?.cancel()
        overridesJob = startUserOverrideJob()
    }

    fun stop() {
        stateHolder.setActive(false)
        stopSelf()
    }

    override fun onDestroy() {
        cancelNoInternetStopJob()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stateHolder.setActive(false)
        AutoTunnelTileRefresher.refresh(this)
        super.onDestroy()
    }

    private fun startUserOverrideJob(): Job =
        lifecycleScope.launch(ioDispatcher) {
            tunnelCoordinator.userOverrideFlow.collect {
                reconciliationMutex.withLock {
                    if (!hasUserOverride) {
                        Timber.d(
                            "User manually overrode Auto Tunnel on current network. Pausing auto decisions."
                        )
                    }
                    hasUserOverride = true
                }
            }
        }

    private fun launchWatcherNotification(
        description: String = getString(R.string.monitoring_state_changes)
    ) {
        val notification =
            notificationService.createNotification(
                AndroidNotificationService.NotificationChannels.AutoTunnel,
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

    // Instead of stopping tunnel right away on no internet, we kick off this job to add short delay
    // and re-evaluation to prevent unwanted stops
    // on flaky networks and network transitions
    private fun scheduleNoInternetStop() {
        noInternetStopJob?.cancel()

        noInternetStopJob =
            lifecycleScope.launch(ioDispatcher) {
                delay(NO_INTERNET_GRACE_PERIOD_MS.milliseconds)

                reconciliationMutex.withLock {
                    val currentNetworkState = networkEngine.stableState.value?.state?.toDomain()

                    val stillNoInternet = currentNetworkState?.hasInternet() == false
                    val stopOnNoInternetEnabled =
                        autoTunnelRepository.flow.firstOrNull()?.isStopOnNoInternetEnabled == true

                    if (stillNoInternet && stopOnNoInternetEnabled) {
                        val currentActiveIds =
                            tunnelCoordinator.backendStatus.value.activeTunnels.keys

                        if (currentActiveIds.isNotEmpty()) {
                            Timber.w(
                                "No internet grace period expired and still no internet. Stopping tunnels: $currentActiveIds"
                            )
                            currentActiveIds.forEach { tunnelId ->
                                tunnelCoordinator.stopTunnel(
                                    tunnelId,
                                    TunnelActionSource.AUTO_TUNNEL,
                                )
                            }
                        }
                    } else {
                        Timber.d(
                            "No internet grace period expired, but internet is back or setting disabled. Doing nothing."
                        )
                    }
                }
            }
    }

    private fun cancelNoInternetStopJob() {
        noInternetStopJob?.cancel()
        noInternetStopJob = null
    }

    private fun startAutoTunnelStateJob(): Job =
        lifecycleScope.launch(ioDispatcher) {
            autoTunnelStateFlow.collectLatest { state ->
                reconciliationMutex.withLock {
                    updateFingerprintIfNeeded(state)
                    val rawEvent = engine.evaluate(state)
                    val event = applyOverrides(rawEvent)
                    Timber.d("AutoTunnel reconciliation event: $event")
                    handleAutoTunnelEvent(event)
                }
            }
        }

    private fun updateFingerprintIfNeeded(state: AutoTunnelState) {
        val currentFingerprint = state.networkFingerPrint

        if (lastNetworkFingerprint != currentFingerprint) {
            if (hasUserOverride) {
                Timber.d("Network fingerprint changed, clearing user override")
            }
            hasUserOverride = false
            lastNetworkFingerprint = currentFingerprint
        }
    }

    private fun applyOverrides(event: AutoTunnelEvent): AutoTunnelEvent {
        return if (hasUserOverride) {
            AutoTunnelEvent.DoNothing
        } else {
            event
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
                                state.settings.trustedNetworkSSIDs.isNotEmpty(),
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
                            notificationService.remove(
                                NotificationService.AUTO_TUNNEL_LOCATION_SERVICES_ID
                            )
                            notificationService.remove(
                                NotificationService.AUTO_TUNNEL_LOCATION_PERMISSION_ID
                            )
                            return@collect
                        }

                        if (!state.locationPermissionsEnabled) {
                            val notification =
                                notificationService.createNotification(
                                    AndroidNotificationService.NotificationChannels.AutoTunnel,
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
                            val notification =
                                notificationService.createNotification(
                                    AndroidNotificationService.NotificationChannels.AutoTunnel,
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
            is AutoTunnelEvent.Sync -> {
                cancelNoInternetStopJob()
                event.stop.forEach { tunnelId ->
                    Timber.d("Stopping tunnel: $tunnelId")
                    tunnelCoordinator.stopTunnel(tunnelId, TunnelActionSource.AUTO_TUNNEL)
                }

                event.start.forEach { config ->
                    Timber.d("Starting tunnel: ${config.name}")
                    tunnelCoordinator.startTunnel(config, TunnelActionSource.AUTO_TUNNEL)
                }
            }
            AutoTunnelEvent.StopAllDueToNoInternet -> scheduleNoInternetStop()
            AutoTunnelEvent.DoNothing -> Unit
        }
    }

    companion object {
        private const val NO_INTERNET_GRACE_PERIOD_MS = 10_000L
    }
}
