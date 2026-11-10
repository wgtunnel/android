package com.zaneschepke.wireguardautotunnel.service.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.di.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import timber.log.Timber

/**
 * Handles taps on [TunnelControlWidget]. Kept separate from the widget provider and not exported,
 * because the provider must be exported for the launcher, and a toggle action on an exported
 * receiver could be triggered by any app.
 */
class TunnelWidgetToggleReceiver : BroadcastReceiver(), KoinComponent {

    private val tunnelCoordinator: TunnelCoordinator by inject()
    private val applicationScope: CoroutineScope by lazy { get(named(Scope.APPLICATION)) }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return

        val pending = goAsync()
        applicationScope.launch {
            try {
                tunnelCoordinator.toggleActiveTunnels()
            } catch (e: Exception) {
                Timber.w(e, "Tunnel widget toggle failed")
            } finally {
                TunnelControlWidget.refresh(context)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.zaneschepke.wireguardautotunnel.widget.TOGGLE_TUNNEL"
    }
}
