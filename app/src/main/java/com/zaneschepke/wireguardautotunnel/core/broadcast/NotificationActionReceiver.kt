package com.zaneschepke.wireguardautotunnel.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zaneschepke.wireguardautotunnel.core.lock.AppLockManager
import com.zaneschepke.wireguardautotunnel.core.orchestration.NotificationActionCoordinator
import com.zaneschepke.wireguardautotunnel.di.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import timber.log.Timber

class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val notificationActionCoordinator: NotificationActionCoordinator by inject()

    private val appLockManager: AppLockManager by inject()

    private val applicationScope: CoroutineScope = get(named(Scope.APPLICATION))

    override fun onReceive(context: Context, intent: Intent) {

        applicationScope.launch {
            // With app lock on, actions go through PinGateActivity
            if (appLockManager.isLockRequired()) {
                Timber.w("Ignoring notification action ${intent.action}, app is locked")
                return@launch
            }
            notificationActionCoordinator.handle(intent)
        }
    }
}
