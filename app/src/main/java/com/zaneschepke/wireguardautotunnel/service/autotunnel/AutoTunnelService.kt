package com.zaneschepke.wireguardautotunnel.service.autotunnel

import android.content.Intent
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.wgtunnel.backend.state.BackendStatus
import com.zaneschepke.networkmonitor.ActiveNetwork
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor
import com.zaneschepke.networkmonitor.StableNetworkEngine
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelOriginHolder
import com.zaneschepke.wireguardautotunnel.di.Dispatcher
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.domain.events.AutoTunnelEvent
import com.zaneschepke.wireguardautotunnel.domain.model.AutoTunnelSettings
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.policy.StopOnUnreachablePolicy
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.state.AutoTunnelState
import com.zaneschepke.wireguardautotunnel.domain.state.toDomain
import com.zaneschepke.wireguardautotunnel.notification.AndroidNotificationService
import com.zaneschepke.wireguardautotunnel.notification.NotificationService
import com.zaneschepke.wireguardautotunnel.notification.TunnelNotificationService
import com.zaneschepke.wireguardautotunnel.service.tile.AutoTunnelTileRefresher
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.extensions.debounceFalling
import com.zaneschepke.wireguardautotunnel.util.extensions.to
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.supervisorScope
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
    private val tunnelOriginHolder: TunnelOriginHolder by inject()
    private val tunnelNotificationService: TunnelNotificationService by inject()
    private var noInternetStopJob: Job? = null
    private val ioScope by lazy { lifecycleScope + ioDispatcher }

    private val unreachableMonitor by lazy {
        UnreachableTunnelMonitor(
            scope = ioScope,
            stopTunnel = { id ->
                Timber.i("Stopping unreachable tunnel $id")
                tunnelCoordinator.stopTunnel(id, TunnelActionSource.AUTO_TUNNEL)
            },
            onStopped = { id ->
                val name = tunnelsRepository.getById(id)?.name ?: id.toString()
                tunnelNotificationService.showUnreachableStop(name)
            },
        )
    }

    private data class PermissionWarningState(
        val detectionMethod: AndroidNetworkMonitor.WifiDetectionMethod,
        val locationServicesEnabled: Boolean,
        val locationPermissionsEnabled: Boolean,
        val ssidReadRequired: Boolean,
    )

    @Volatile private var hasUserOverride = false
    private var lastNetworkKey: String? = null
    // Mirrors AutoTunnelState.confirmedHasUsableNetwork so scheduleNoInternetStop's delayed
    // check uses the same debounced signal the engine used to decide to arm it, instead of
    // re-deriving a fresh (not debounced) reading that could disagree by the time it fires.
    @Volatile private var lastConfirmedHasUsableNetwork = false

    @OptIn(FlowPreview::class)
    private val autoTunnelStateFlow: Flow<AutoTunnelState> by lazy {
        // Add stabilization to network identity
        val networkFlow =
            networkEngine.stableState
                .mapNotNull { it?.state?.toDomain() }
                .debounce(NETWORK_IDENTITY_SETTLE_MS.milliseconds)

        val settingsFlow = combineSettings()

        val backendFlow =
            tunnelCoordinator.backendStatus
                .distinctUntilChanged { old, new ->
                    old.activeTunnels.keys == new.activeTunnels.keys
                }
                .debounce(300L.milliseconds)

        // Detected captive portal state is trusted immediately, but cleared state
        // is only trusted once it's held for CAPTIVE_PORTAL_CLEAR_CONFIRM_MS without a change
        // to prevent flapping on flaky networks.
        val confirmedCaptivePortalFlow =
            networkFlow
                .map {
                    (it.activeNetwork as? ActiveNetwork.Wifi)?.requiresCaptivePortalLogin == true
                }
                .distinctUntilChanged()
                .debounceFalling(CAPTIVE_PORTAL_CLEAR_CONFIRM_MS.milliseconds)

        // Having a usable network is believed immediately, but losing it is only believed once
        // it's held false for NO_INTERNET_CONFIRM_MS without recovering as Android's connectivity
        // validation can flap for an extended period during network transitions
        val confirmedHasUsableNetworkFlow =
            networkFlow
                .map { it.hasUsableNetwork }
                .distinctUntilChanged()
                .debounceFalling(NO_INTERNET_CONFIRM_MS.milliseconds)

        combine(
                networkFlow,
                settingsFlow,
                backendFlow,
                confirmedCaptivePortalFlow,
                confirmedHasUsableNetworkFlow,
            ) { network, settings, backend, confirmedCaptivePortal, confirmedHasUsableNetwork ->
                AutoTunnelState(
                    networkState = network,
                    settings = settings.second,
                    tunnelMode = settings.first,
                    tunnels = settings.third,
                    backendStatus = backend,
                    confirmedCaptivePortal = confirmedCaptivePortal,
                    confirmedHasUsableNetwork = confirmedHasUsableNetwork,
                )
            }
            .distinctUntilChanged()
            .shareIn(
                scope = ioScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
                replay = 1,
            )
    }

    override fun onCreate() {
        super.onCreate()
        stateHolder.setActive(true)
        launchWatcherNotification()
        observeActiveState()
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
    }

    private fun observeActiveState() =
        lifecycleScope.launch(ioDispatcher) {
            stateHolder.active.collectLatest { isActive ->
                if (!isActive) return@collectLatest
                supervisorScope {
                    launch { runAutoTunnelStateJob() }
                    launch { runLocationPermissionsNotificationJob() }
                    launch { runUserOverrideJob() }
                    launch { runUnreachableJob() }
                }
            }
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

    private suspend fun runUserOverrideJob() {
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
        if (noInternetStopJob?.isActive == true) return

        noInternetStopJob =
            lifecycleScope.launch(ioDispatcher) {
                delay(NO_INTERNET_GRACE_PERIOD_MS.milliseconds)

                reconciliationMutex.withLock {
                    val stillNoUsableNetwork = !lastConfirmedHasUsableNetwork
                    val stopOnNoInternetEnabled =
                        autoTunnelRepository.flow.firstOrNull()?.isStopOnNoInternetEnabled == true

                    if (stillNoUsableNetwork && stopOnNoInternetEnabled) {
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

    private suspend fun runAutoTunnelStateJob() {
        // Add startup settle to prevent flapping after OS kill
        var hasSettled = false
        combine(autoTunnelStateFlow, unreachableMonitor.suspended) { state, suspended ->
                state to suspended
            }
            .collectLatest { (state, suspended) ->
                if (!hasSettled) {
                    delay(STARTUP_SETTLE_MS)
                    hasSettled = true
                }
                reconciliationMutex.withLock {
                    lastConfirmedHasUsableNetwork = state.confirmedHasUsableNetwork
                    updateFingerprintIfNeeded(state)
                    val rawEvent = engine.evaluate(state)
                    val event = applyOverrides(rawEvent).withoutSuspendedStarts(suspended)
                    Timber.d("AutoTunnel reconciliation event: $event")
                    handleAutoTunnelEvent(event)
                }
            }
    }

    // Only Auto-Tunnel's own tunnels are stopped; manually started ones are left alone.
    private suspend fun runUnreachableJob() {
        combine(
                autoTunnelStateFlow,
                tunnelCoordinator.backendStatus,
                tunnelOriginHolder.origins,
                settingsRepository.flow,
            ) { state, status, origins, general ->
                buildUnreachableTunnelSnapshot(
                    state = state,
                    status = status,
                    origins = origins,
                    general = general,
                    androidVpnLockdown = isAndroidVpnLockdown(),
                )
            }
            .distinctUntilChanged()
            .collect(unreachableMonitor::update)
    }

    // always_on_vpn_lockdown is a hidden setting, so this is best effort; unreadable counts as on.
    private fun isAndroidVpnLockdown(): Boolean =
        try {
            Settings.Secure.getInt(contentResolver, ALWAYS_ON_VPN_LOCKDOWN, 0) == 1
        } catch (e: SecurityException) {
            Timber.w(e, "Unable to read always-on VPN lockdown setting")
            true
        }

    private fun updateFingerprintIfNeeded(state: AutoTunnelState) {
        val networkKey = state.networkKey()

        if (lastNetworkKey != networkKey) {
            if (hasUserOverride) {
                Timber.d("Network fingerprint changed, clearing user override")
            }
            hasUserOverride = false
            lastNetworkKey = networkKey
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

    private suspend fun runLocationPermissionsNotificationJob() {
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
        private const val CAPTIVE_PORTAL_CLEAR_CONFIRM_MS = 8_000L
        private const val NO_INTERNET_CONFIRM_MS = 8_000L
        private const val STARTUP_SETTLE_MS = 2_000L
        private const val NETWORK_IDENTITY_SETTLE_MS = 500L
        private const val ALWAYS_ON_VPN_LOCKDOWN = "always_on_vpn_lockdown"
    }
}

internal fun buildUnreachableTunnelSnapshot(
    state: AutoTunnelState,
    status: BackendStatus,
    origins: Map<Int, TunnelActionSource>,
    general: GeneralSettings,
    androidVpnLockdown: Boolean,
): UnreachableTunnelSnapshot {
    val hasUsableNetwork = state.confirmedHasUsableNetwork
    val autoStarted =
        status.activeTunnels.filterKeys { origins[it] == TunnelActionSource.AUTO_TUNNEL }
    val tunnelsById = state.tunnels.associateBy(TunnelConfig::id)

    return UnreachableTunnelSnapshot(
        // Stopping the tunnel under lockdown would block all traffic instead.
        enabled =
            state.settings.isAutoTunnelEnabled &&
                state.settings.isStopOnUnreachableEnabled &&
                state.tunnelMode != TunnelMode.LOCK_DOWN &&
                !status.killSwitch.enabled &&
                !androidVpnLockdown,
        networkKey = state.networkKey(),
        armFailureGracePeriodsMs =
            autoStarted
                .filterValues { it.shouldArmFailureRecovery(hasUsableNetwork) }
                .mapValues { (id, _) ->
                    StopOnUnreachablePolicy.gracePeriodMs(tunnelsById[id], general)
                },
        keepFailureIds =
            autoStarted.filterValues { it.shouldKeepFailureRecoveryEpisode(hasUsableNetwork) }.keys,
        connectedIds = status.activeTunnels.filterValues { it.isTransportHealthy() }.keys,
    )
}

internal fun AutoTunnelEvent.withoutSuspendedStarts(suspended: Set<Int>): AutoTunnelEvent {
    if (this !is AutoTunnelEvent.Sync) return this
    // Sync is an atomic replacement in the current single-tunnel design. If its replacement is
    // cooling down, retaining only the stop would tear down the working tunnel.
    return if (start.any { it.id in suspended }) AutoTunnelEvent.DoNothing else this
}

private fun AutoTunnelState.networkKey(): String {
    val needsBSSIDAwareness =
        settings.trustedNetworkBSSIDs.isNotEmpty() || tunnels.any { it.tunnelBSSIDs.isNotEmpty() }
    return networkState.activeNetwork.key(needsBSSIDAwareness)
}
