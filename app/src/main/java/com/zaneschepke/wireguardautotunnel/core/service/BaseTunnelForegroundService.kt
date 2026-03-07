package com.zaneschepke.wireguardautotunnel.core.service

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.notification.NotificationManager
import com.zaneschepke.wireguardautotunnel.core.notification.WireGuardNotification
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.di.Dispatcher
import com.zaneschepke.wireguardautotunnel.domain.enums.NotificationAction
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.util.extensions.distinctByKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import timber.log.Timber

abstract class BaseTunnelForegroundService : LifecycleService(), TunnelService {

    private val notificationManager: NotificationManager by inject()

    private val serviceManager: ServiceManager by inject()

    private val tunnelManager: TunnelManager by inject()

    private val ioDispatcher: CoroutineDispatcher by inject(named(Dispatcher.IO))

    private val settingsRepository: GeneralSettingRepository by inject()

    private val tunnelsRepository: TunnelRepository by inject()

    protected abstract val fgsType: Int

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return LocalBinder(this)
    }

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NotificationManager.VPN_NOTIFICATION_ID,
            onCreateNotification(),
            fgsType,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ServiceCompat.startForeground(
            this,
            NotificationManager.VPN_NOTIFICATION_ID,
            onCreateNotification(),
            fgsType,
        )
        if (
            intent == null ||
                intent.component == null ||
                (intent.component?.packageName != this.packageName)
        ) {
            Timber.d("Service started by Always-on VPN feature")
            lifecycleScope.launch {
                val settings = settingsRepository.getGeneralSettings()
                if (settings.isAlwaysOnVpnEnabled) {
                    val tunnel = tunnelsRepository.getDefaultTunnel()
                    tunnel?.let { tunnelManager.startTunnel(it) }
                } else {
                    Timber.w("Always-on VPN is not enabled in app settings")
                }
            }
        } else {
            start()
        }
        return START_STICKY
    }

    override fun start() {
        lifecycleScope.launch(ioDispatcher) {
            tunnelManager.activeTunnels.distinctByKeys().collect { activeTunnels ->
                val activeTunConfigs = activeTunnels.keys
                val tunnels = tunnelsRepository.getAll()
                val activeConfigs = tunnels.filter { activeTunConfigs.contains(it.id) }
                updateServiceNotification(activeConfigs)
            }
        }
    }

    // TODO Would be cool to have this include kill switch
    private fun updateServiceNotification(activeConfigs: List<TunnelConfig>) {
        val notification =
            when (activeConfigs.size) {
                0 -> onCreateNotification()
                1 -> createTunnelNotification(activeConfigs.first())
                else -> createTunnelsNotification()
            }
        ServiceCompat.startForeground(
            this,
            NotificationManager.VPN_NOTIFICATION_ID,
            notification,
            fgsType,
        )
    }

    override fun stop() {
        Timber.d("Stop called")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceManager.handleTunnelServiceDestroy()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Timber.d("onDestroy")
        super.onDestroy()
    }

    private fun createTunnelNotification(tunnelConfig: TunnelConfig): Notification {
        return notificationManager.createNotification(
            WireGuardNotification.NotificationChannels.VPN,
            title = "${getString(R.string.tunnel_running)} - ${tunnelConfig.name}",
            actions =
                listOf(
                    notificationManager.createNotificationAction(
                        NotificationAction.TUNNEL_OFF,
                        tunnelConfig.id,
                    )
                ),
            onGoing = true,
            groupKey = NotificationManager.VPN_GROUP_KEY,
            isGroupSummary = true,
        )
    }

    private fun createTunnelsNotification(): Notification {
        return notificationManager.createNotification(
            WireGuardNotification.NotificationChannels.VPN,
            title = "${getString(R.string.tunnel_running)} - ${getString(R.string.multiple)}",
            actions =
                listOf(
                    notificationManager.createNotificationAction(NotificationAction.TUNNEL_OFF, 0)
                ),
            groupKey = NotificationManager.VPN_GROUP_KEY,
            isGroupSummary = true,
        )
    }

    private fun onCreateNotification(): Notification {
        return notificationManager.createNotification(
            WireGuardNotification.NotificationChannels.VPN,
            title = getString(R.string.tunnel_starting),
            groupKey = NotificationManager.VPN_GROUP_KEY,
            isGroupSummary = true,
        )
    }
}
