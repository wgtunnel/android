package com.zaneschepke.wireguardautotunnel.core.event

import com.zaneschepke.tunnel.event.TunnelEvent
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.core.notification.TunnelNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class TunnelEventDispatcher(private val notificationManager: TunnelNotificationService) {

    fun bind(
        scope: CoroutineScope,
        providerEvents: Flow<TunnelEvent>,
        providerStatus: StateFlow<BackendStatus>,
        coordinatorErrors: Flow<TunnelErrorEvent>,
    ) {

        // informational events
        providerEvents
            .onEach { event ->
                when (event) {
                    is TunnelEvent.FallbackToIpv4 -> {
                        notificationManager.showIpv4Fallback(event.tunnelId)
                    }

                    is TunnelEvent.RecoveredToIpv6 -> {
                        notificationManager.showIpv6Recovery(event.tunnelId)
                    }

                    is TunnelEvent.DynamicDnsUpdate -> {
                        notificationManager.showDynamicDnsUpdate(event.tunnelId)
                    }
                }
            }
            .launchIn(scope)

        // errors from the coordinator
        coordinatorErrors
            .onEach { error ->
                when (error) {
                    is TunnelErrorEvent.VpnPermissionDenied -> {
                        notificationManager.showVpnRequired()
                    }

                    is TunnelErrorEvent.StateConflict -> {
                        notificationManager.showStateConflict(error.tunnelId)
                    }

                    is TunnelErrorEvent.InternalFailure -> {
                        notificationManager.showError(error.message)
                    }
                }
            }
            .launchIn(scope)

        // update persistent notification for services with the tunnel states
        providerStatus
            .onEach { status -> notificationManager.updatePersistentNotifications(status) }
            .launchIn(scope)
    }
}
