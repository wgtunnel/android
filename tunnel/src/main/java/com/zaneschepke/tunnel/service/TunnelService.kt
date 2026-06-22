package com.zaneschepke.tunnel.service

import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.service.ServiceHolder.Companion.alwaysOnCallback
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class TunnelService : LifecycleService() {

    private val backend: Backend by inject(Backend::class.java)
    private val serviceHolder: ServiceHolder by inject(ServiceHolder::class.java)
    private val shutdownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val notificationManager: NotificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    @Volatile private var userActivatedShutdown = false

    override fun onCreate() {
        serviceHolder.set(this)
        launchForegroundNotification()
        observeProxyPersistentNotification()
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        serviceHolder.set(this)
        launchForegroundNotification()

        // Service restarted by system, reuse always-on VPN callback
        if (
            intent == null ||
                intent.component == null ||
                (intent.component!!.packageName != packageName)
        ) {
            Timber.d("TunnelService started by system")
            alwaysOnCallback?.get()?.alwaysOnTriggered()
        }

        return START_STICKY
    }

    @OptIn(FlowPreview::class)
    private fun observeProxyPersistentNotification() {
        lifecycleScope.launch {
            backend.status
                .distinctUntilChangedBy { it.toNotificationComparisonKey() }
                .debounce(700.milliseconds)
                .collect { status ->
                    val notification =
                        backend.applicationProvider.buildProxyPersistentNotification(status)
                    notificationManager.notify(
                        backend.applicationProvider.proxyNotificationId,
                        notification,
                    )
                }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun shutdown() {
        userActivatedShutdown = true
        stopSelf()
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        serviceHolder.clearTunnelService()
        if (!userActivatedShutdown) {
            Timber.d("Service being killed by system, clean up tunnels")
            shutdownScope.launch {
                // TODO eventually, this should only shut down proxy mode tunnels with future multi
                // tunnel
                backend.stopAllActiveTunnels()
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    fun launchForegroundNotification() {
        ServiceCompat.startForeground(
            this,
            backend.applicationProvider.proxyNotificationId,
            backend.applicationProvider.proxyInitNotification,
            ServiceHolder.SPECIAL_USE_SERVICE_TYPE_ID,
        )
    }
}
