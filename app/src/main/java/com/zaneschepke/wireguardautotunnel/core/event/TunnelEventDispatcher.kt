package com.zaneschepke.wireguardautotunnel.core.event

import com.zaneschepke.tunnel.event.TunnelEvent
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.core.notification.TunnelNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
            .distinctUntilChanged()
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

                    is TunnelEvent.NoRootShellAccess -> {
                        notificationManager.showRootShellAccess()
                    }
                }
            }
            .launchIn(scope)

        // errors from the coordinator
        coordinatorErrors
            .distinctUntilChanged()
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
            .map { it.activeTunnels }
            .distinctUntilChangedBy { map ->
                val stateSignature =
                    map.entries
                        .sortedBy { it.key }
                        .map { (_, tunnel) -> tunnel.transportState to tunnel.bootstrapState }
                map.size to stateSignature
            }
            .onEach { status -> notificationManager.updatePersistentNotifications(status) }
            .launchIn(scope)
    }
}
