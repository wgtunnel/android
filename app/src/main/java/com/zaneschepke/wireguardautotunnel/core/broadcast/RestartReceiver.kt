package com.zaneschepke.wireguardautotunnel.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zaneschepke.logcatter.LogReader
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.di.Scope
import com.zaneschepke.wireguardautotunnel.domain.repository.AppStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import timber.log.Timber

class RestartReceiver : BroadcastReceiver(), KoinComponent {

    private val applicationScope: CoroutineScope = get(named(Scope.APPLICATION))

    private val tunnelManager: TunnelManager by inject()

    private val appStateRepository: AppStateRepository by inject()

    private val logReader: LogReader by inject()

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("RestartReceiver triggered with action: ${intent.action}")
        applicationScope.launch {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON",
                "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                    tunnelManager.handleReboot()
                }
                Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    Timber.i("Restoring state on package upgrade")
                    tunnelManager.handleRestore()
                    logReader.deleteAndClearLogs()
                    appStateRepository.setShouldShowDonationSnackbar(true)
                }
            }
        }
    }
}
