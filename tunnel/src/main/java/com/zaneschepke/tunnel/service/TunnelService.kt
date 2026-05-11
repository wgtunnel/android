package com.zaneschepke.tunnel.service

import android.content.Intent
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.backend.ServiceHolder
import com.zaneschepke.tunnel.backend.ServiceHolder.Companion.alwaysOnCallback
import com.zaneschepke.tunnel.model.BackendMode
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.*
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class TunnelService : LifecycleService() {

    private val backend: Backend by inject(Backend::class.java)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        ServiceHolder.tunnelService.complete(this)
        launchForegroundNotification()
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ServiceHolder.tunnelService.complete(this)
        launchForegroundNotification()

        // Service restarted by system, reuse always-on VPN callback
        if (
            intent == null ||
                intent.component == null ||
                (intent.component!!.packageName != packageName)
        ) {
            Timber.d("TunnelService started by system")
            alwaysOnCallback?.alwaysOnTriggered()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.launch {
            backend.stopAllOfType(BackendMode.Proxy.Standard::class)
            serviceScope.cancel()
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        if (!ServiceHolder.tunnelService.isDone) {
            ServiceHolder.tunnelService.cancel(false)
        }
        ServiceHolder.tunnelService = CompletableFuture<TunnelService>()
        super.onDestroy()
    }

    fun launchForegroundNotification() {
        ServiceCompat.startForeground(
            this,
            backend.notificationProvider.proxyNotificationId,
            backend.notificationProvider.proxyInitNotification,
            SPECIAL_USE_SERVICE_TYPE_ID,
        )
    }

    companion object {
        private const val SPECIAL_USE_SERVICE_TYPE_ID = 1 shl 30
    }
}
