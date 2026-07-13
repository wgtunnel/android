package com.zaneschepke.tunnel.service

import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.zaneschepke.tunnel.backend.Backend
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class VpnCompanionService : LifecycleService() {

    private val backend: Backend by inject()
    private val serviceManager: ServiceManager by inject(ServiceManager::class.java)

    private val notificationManager: NotificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    fun shutdown() {
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        serviceManager.set(this)
        Timber.d("CompanionService created")
        launchForegroundNotification()
        observeVpnPersistentNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        serviceManager.set(this)
        val isSystemRestart =
            intent == null ||
                intent.component == null ||
                intent.component!!.packageName != packageName

        if (isSystemRestart) {
            Timber.d("VpnCompanionService started by system")
            launchForegroundNotification()
        }
        return START_STICKY
    }

    private fun launchForegroundNotification() {
        ServiceCompat.startForeground(
            this,
            backend.applicationProvider.vpnNotificationId,
            backend.applicationProvider.vpnInitNotification,
            ServiceManager.SPECIAL_USE_SERVICE_TYPE_ID,
        )
    }

    @OptIn(FlowPreview::class)
    private fun observeVpnPersistentNotification() {
        lifecycleScope.launch {
            backend.status
                .distinctUntilChangedBy { it.toNotificationComparisonKey() }
                .debounce(700.milliseconds)
                .collect { status ->
                    val notification =
                        backend.applicationProvider.buildVpnPersistentNotification(status)
                    notificationManager.notify(
                        backend.applicationProvider.vpnNotificationId,
                        notification,
                    )
                }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        Timber.d("CompanionService destroyed")
        serviceManager.clearCompanionService()
        super.onDestroy()
    }
}
