package com.zaneschepke.wireguardautotunnel.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.di.Scope
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class KernelReceiver : BroadcastReceiver(), KoinComponent {

    private val applicationScope: CoroutineScope by inject(named(Scope.APPLICATION))
    private val tunnelRepository: TunnelRepository by inject()
    private val tunnelManager: TunnelManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        applicationScope.launch {
            if (action == REFRESH_TUNNELS_ACTION) {
                tunnelManager.runningTunnelNames().forEach { name ->
                    val tunnel = tunnelRepository.findByTunnelName(name)
                    tunnel?.let { tunnelRepository.save(it.copy(isActive = true)) }
                }
            }
        }
    }

    companion object {
        const val REFRESH_TUNNELS_ACTION = "com.wireguard.android.action.REFRESH_TUNNEL_STATES"
    }
}
