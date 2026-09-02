package com.zaneschepke.wireguardautotunnel.core.tunnel

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.wgtunnel.backend.AndroidApplicationProvider
import com.wgtunnel.backend.model.BackendMode
import com.wgtunnel.backend.state.ActiveTunnel
import com.wgtunnel.backend.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.MainActivity
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.GeneralSettings
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.notification.AndroidNotificationService
import com.zaneschepke.wireguardautotunnel.notification.NotificationService
import com.zaneschepke.wireguardautotunnel.notification.TunnelNotificationLine
import com.zaneschepke.wireguardautotunnel.notification.TunnelNotificationOptions
import com.zaneschepke.wireguardautotunnel.notification.TunnelNotificationService
import com.zaneschepke.wireguardautotunnel.service.tile.TunnelTileRefresher
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppProvider(
    private val notificationService: NotificationService,
    private val tunnelNotificationService: TunnelNotificationService,
    private val tunnelRepository: TunnelRepository,
    private val settingsRepository: GeneralSettingRepository,
    private val tunnelOriginHolder: TunnelOriginHolder,
) : AndroidApplicationProvider {

    override val context: Context = notificationService.context

    @Volatile private var notificationOptionsCache = TunnelNotificationOptions()

    private val notificationSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val persistentNotificationSignals: Flow<Unit> = notificationSignals.asSharedFlow()

    override fun refreshStatusUi() {
        TunnelTileRefresher.refresh(context)
    }

    override fun createVpnConfigurePendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override val vpnInitNotification: Notification
        get() = initNotification(AndroidNotificationService.NotificationChannels.Tunnel.VPN)

    override val proxyInitNotification: Notification
        get() = initNotification(AndroidNotificationService.NotificationChannels.Tunnel.Proxy)

    private fun initNotification(
        channel: AndroidNotificationService.NotificationChannels
    ): Notification {
        val promote = shouldPromoteLiveUpdates()
        val kind =
            when (channel) {
                is AndroidNotificationService.NotificationChannels.Tunnel.VPN ->
                    context.getString(R.string.vpn)
                is AndroidNotificationService.NotificationChannels.Tunnel.Proxy ->
                    context.getString(R.string.proxy)
                else -> context.getString(R.string.vpn)
            }
        return notificationService.createNotification(
            channel = channel,
            title = context.getString(R.string.initializing),
            onGoing = true,
            showTimestamp = false,
            requestPromotedOngoing = promote,
            shortCriticalText = if (promote) kind else null,
        )
    }

    private fun shouldPromoteLiveUpdates(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        return notificationOptionsCache.liveUpdatesEnabled &&
            NotificationManagerCompat.from(context).canPostPromotedNotifications()
    }

    override val vpnNotificationId: Int
        get() = NotificationService.VPN_NOTIFICATION_ID

    override val proxyNotificationId: Int
        get() = NotificationService.PROXY_NOTIFICATION_ID

    override suspend fun buildVpnPersistentNotification(status: BackendStatus): Notification {
        val lines = computeVpnNotificationLines(status)
        return tunnelNotificationService.buildVpnPersistentNotification(
            lines,
            notificationOptions(),
            lockdown = status.killSwitch.enabled || lines.values.any { it.lockdown },
        )
    }

    override suspend fun buildProxyPersistentNotification(status: BackendStatus): Notification {
        val lines = computeProxyNotificationLines(status)
        return tunnelNotificationService.buildProxyPersistentNotification(
            lines,
            notificationOptions(),
        )
    }

    override fun persistentNotificationKey(status: BackendStatus): Any {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            return status.toNotificationComparisonKey()
        }
        val prefs = notificationOptionsCache
        val extrasKey =
            status.activeTunnels.mapValues { (_, tunnel) ->
                listOf(
                    if (prefs.showRecovery) tunnel.recoveryAttempts else 0,
                    if (prefs.showTransfer) tunnel.lastStatsAtMs else 0L,
                    if (prefs.liveUpdatesEnabled) tunnel.uptime else null,
                    prefs.showFailureTint &&
                        DisplayTunnelState.from(tunnel) == DisplayTunnelState.HandshakeFailure,
                )
            }
        return listOf(status.toNotificationComparisonKey(), extrasKey, prefs)
    }

    fun bind(scope: CoroutineScope) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        scope.launch {
            settingsRepository.flow
                .map { notificationPrefs(it) }
                .distinctUntilChanged()
                .collect {
                    notificationOptionsCache = it
                    notificationSignals.tryEmit(Unit)
                }
        }
    }

    private suspend fun notificationOptions(): TunnelNotificationOptions {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return TunnelNotificationOptions()
        return notificationPrefs(settingsRepository.getGeneralSettings())
    }

    private fun notificationPrefs(settings: GeneralSettings): TunnelNotificationOptions =
        TunnelNotificationOptions(
            liveUpdatesEnabled = settings.isLiveUpdatesEnabled,
            showOrigin = settings.isNotificationOriginEnabled,
            showTransfer = settings.isNotificationTransferEnabled,
            showRecovery = settings.isNotificationRecoveryEnabled,
            showFailureTint = settings.isNotificationFailureTintEnabled,
        )

    private suspend fun computeVpnNotificationLines(
        status: BackendStatus
    ): Map<Int, TunnelNotificationLine> {
        val activeTunnels = status.activeTunnels
        val allTunnels = tunnelRepository.userTunnelsFlow.first()
        return activeTunnels
            .mapNotNull { (id, activeTunnel) ->
                val mode = activeTunnel.mode ?: return@mapNotNull null
                if (mode !is BackendMode.Vpn && mode !is BackendMode.Proxy.KillSwitchPrimary)
                    return@mapNotNull null
                val tunnel = allTunnels.find { it.id == id } ?: return@mapNotNull null
                notificationLine(
                    id,
                    tunnel.name,
                    activeTunnel,
                    lockdown = mode is BackendMode.Proxy.KillSwitchPrimary,
                )
            }
            .associateBy { it.id }
    }

    private suspend fun computeProxyNotificationLines(
        status: BackendStatus
    ): Map<Int, TunnelNotificationLine> {
        val activeTunnels = status.activeTunnels
        val allTunnels = tunnelRepository.userTunnelsFlow.first()
        return activeTunnels
            .mapNotNull { (id, activeTunnel) ->
                val mode = activeTunnel.mode ?: return@mapNotNull null
                if (mode !is BackendMode.Proxy.Standard) return@mapNotNull null
                val tunnel = allTunnels.find { it.id == id } ?: return@mapNotNull null
                notificationLine(id, tunnel.name, activeTunnel)
            }
            .associateBy { it.id }
    }

    private fun notificationLine(
        id: Int,
        name: String,
        activeTunnel: ActiveTunnel,
        lockdown: Boolean = false,
    ): TunnelNotificationLine {
        val peers = activeTunnel.activeConfig?.peers.orEmpty()
        return TunnelNotificationLine(
            id = id,
            name = name,
            displayState = DisplayTunnelState.from(activeTunnel),
            startedAtMillis = activeTunnel.uptime,
            origin = tunnelOriginHolder.origins.value[id],
            rxBytes = peers.sumOf { it.rxBytes ?: 0L },
            txBytes = peers.sumOf { it.txBytes ?: 0L },
            recoveryAttempts = activeTunnel.recoveryAttempts,
            lockdown = lockdown,
        )
    }
}
