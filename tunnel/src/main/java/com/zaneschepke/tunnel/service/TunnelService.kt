package com.zaneschepke.tunnel.service

import android.content.Intent
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.backend.ServiceHolder
import com.zaneschepke.tunnel.backend.ServiceHolder.Companion.alwaysOnCallback
import com.zaneschepke.tunnel.model.BackendMode
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class TunnelService : LifecycleService() {

    private val backend: Backend by inject(Backend::class.java)
    private val serviceHolder: ServiceHolder by inject(ServiceHolder::class.java)

    override fun onCreate() {
        ServiceHolder.tunnelService.complete(this)
        serviceHolder.ensureNativeCallbacksRegistered()
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
            alwaysOnCallback?.get()?.alwaysOnTriggered()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        runBlocking { backend.stopAllOfType(BackendMode.Proxy.Standard::class) }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        serviceHolder.clear(this)
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
