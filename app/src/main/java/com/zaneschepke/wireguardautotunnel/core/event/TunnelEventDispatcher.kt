package com.zaneschepke.wireguardautotunnel.core.event

import android.content.Context
import com.dokar.sonner.ToastType
import com.zaneschepke.tunnel.event.TunnelEvent
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.lifecyle.AppVisibilityObserver
import com.zaneschepke.wireguardautotunnel.notification.TunnelNotificationLine
import com.zaneschepke.wireguardautotunnel.notification.TunnelNotificationService
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState
import com.zaneschepke.wireguardautotunnel.util.StringValue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TunnelEventDispatcher(
    private val notificationManager: TunnelNotificationService,
    private val tunnelRepository: TunnelRepository,
    private val context: Context,
    private val appVisibilityObserver: AppVisibilityObserver,
    private val globalEffectRepository: GlobalEffectRepository,
) {

    @OptIn(FlowPreview::class)
    fun bind(
        scope: CoroutineScope,
        providerEvents: Flow<TunnelEvent>,
        providerStatus: StateFlow<BackendStatus>,
        coordinatorErrors: Flow<TunnelErrorEvent>,
        tunnelDisplayStates: StateFlow<Map<Int, DisplayTunnelState>>,
    ) {

        // Informational events from tunnel backend
        providerEvents
            .onEach { event ->
                when (event) {
                    is TunnelEvent.FallbackToIpv4 -> {
                        val name = getTunnelName(event.tunnelId)
                        showOrNotify(
                            scope = scope,
                            foregroundAction = {
                                globalEffectRepository.post(
                                    GlobalSideEffect.Snackbar(
                                        message =
                                            StringValue.DynamicString(
                                                context.getString(
                                                    R.string.notification_ipv4_fallback_message,
                                                    name,
                                                )
                                            ),
                                        type = ToastType.Info,
                                    )
                                )
                            },
                            backgroundAction = { notificationManager.showIpv4Fallback(name) },
                        )
                    }

                    is TunnelEvent.RecoveredToIpv6 -> {
                        val name = getTunnelName(event.tunnelId)
                        showOrNotify(
                            scope = scope,
                            foregroundAction = {
                                globalEffectRepository.post(
                                    GlobalSideEffect.Snackbar(
                                        message =
                                            StringValue.DynamicString(
                                                context.getString(
                                                    R.string.notification_ipv6_recovery_message,
                                                    name,
                                                )
                                            ),
                                        type = ToastType.Success,
                                    )
                                )
                            },
                            backgroundAction = { notificationManager.showIpv6Recovery(name) },
                        )
                    }

                    is TunnelEvent.DynamicDnsUpdate -> {
                        val name = getTunnelName(event.tunnelId)
                        showOrNotify(
                            scope = scope,
                            foregroundAction = {
                                globalEffectRepository.post(
                                    GlobalSideEffect.Snackbar(
                                        message =
                                            StringValue.DynamicString(
                                                context.getString(
                                                    R.string.notification_dynamic_dns_message,
                                                    name,
                                                )
                                            ),
                                        type = ToastType.Info,
                                    )
                                )
                            },
                            backgroundAction = { notificationManager.showDynamicDnsUpdate(name) },
                        )
                    }

                    is TunnelEvent.NoRootShellAccess -> {
                        showOrNotify(
                            scope = scope,
                            foregroundAction = {
                                globalEffectRepository.post(
                                    GlobalSideEffect.Snackbar(
                                        message =
                                            StringValue.DynamicString(
                                                context.getString(R.string.error_root_denied)
                                            ),
                                        type = ToastType.Error,
                                    )
                                )
                            },
                            backgroundAction = { notificationManager.showRootShellAccess() },
                        )
                    }
                }
            }
            .launchIn(scope)

        // Errors from our tunnel coordinator
        coordinatorErrors
            .onEach { error ->
                when (error) {
                    is TunnelErrorEvent.VpnPermissionDenied -> {
                        showOrNotify(
                            scope = scope,
                            foregroundAction = {
                                globalEffectRepository.post(
                                    GlobalSideEffect.Snackbar(
                                        message =
                                            StringValue.DynamicString(
                                                context.getString(R.string.vpn_permission_required)
                                            ),
                                        type = ToastType.Error,
                                    )
                                )
                            },
                            backgroundAction = { notificationManager.showVpnRequired() },
                        )
                    }

                    is TunnelErrorEvent.InternalFailure -> {
                        showOrNotify(
                            scope = scope,
                            foregroundAction = {
                                globalEffectRepository.post(
                                    GlobalSideEffect.Snackbar(
                                        message = StringValue.DynamicString(error.message),
                                        type = ToastType.Error,
                                    )
                                )
                            },
                            backgroundAction = { notificationManager.showError(error.message) },
                        )
                    }

                    is TunnelErrorEvent.Socks5PortUnavailable -> {
                        val name = getTunnelName(error.tunnelId)
                        val message =
                            context.getString(R.string.error_socks5_port_unavailable, error.port)

                        showOrNotify(
                            scope = scope,
                            foregroundAction = {
                                globalEffectRepository.post(
                                    GlobalSideEffect.Snackbar(
                                        message = StringValue.DynamicString(message),
                                        type = ToastType.Error,
                                    )
                                )
                            },
                            backgroundAction = {
                                notificationManager.showSocks5PortUnavailable(error.port, name)
                            },
                        )
                    }

                    is TunnelErrorEvent.HttpPortUnavailable -> {
                        val name = getTunnelName(error.tunnelId)
                        val message =
                            context.getString(R.string.error_http_port_unavailable, error.port)

                        showOrNotify(
                            scope = scope,
                            foregroundAction = {
                                globalEffectRepository.post(
                                    GlobalSideEffect.Snackbar(
                                        message = StringValue.DynamicString(message),
                                        type = ToastType.Error,
                                    )
                                )
                            },
                            backgroundAction = {
                                notificationManager.showHttpPortUnavailable(error.port, name)
                            },
                        )
                    }
                }
            }
            .launchIn(scope)

        // vpn
        combine(
                providerStatus.map { it.activeTunnels },
                tunnelRepository.userTunnelsFlow,
                tunnelDisplayStates,
            ) { activeTunnels, allTunnels, displayStates ->
                activeTunnels
                    .mapNotNull { (id, activeTunnel) ->
                        val mode = activeTunnel.mode ?: return@mapNotNull null
                        if (
                            mode !is BackendMode.Vpn && mode !is BackendMode.Proxy.KillSwitchPrimary
                        ) {
                            return@mapNotNull null
                        }
                        val tunnel = allTunnels.find { it.id == id } ?: return@mapNotNull null

                        val displayState =
                            displayStates[id] ?: DisplayTunnelState.from(activeTunnel)

                        TunnelNotificationLine(
                            id = id,
                            name = tunnel.name,
                            displayState = displayState,
                        )
                    }
                    .associateBy { it.id }
            }
            .distinctUntilChanged()
            .debounce(500.milliseconds) // give the service notification time to display
            .onEach { vpnLines -> notificationManager.updateVpnPersistentNotification(vpnLines) }
            .launchIn(scope)

        // proxy
        combine(
                providerStatus.map { it.activeTunnels },
                tunnelRepository.userTunnelsFlow,
                tunnelDisplayStates,
            ) { activeTunnels, allTunnels, displayStates ->
                activeTunnels
                    .mapNotNull { (id, activeTunnel) ->
                        val mode = activeTunnel.mode ?: return@mapNotNull null
                        if (mode !is BackendMode.Proxy.Standard) return@mapNotNull null

                        val tunnel = allTunnels.find { it.id == id } ?: return@mapNotNull null
                        val displayState =
                            displayStates[id] ?: DisplayTunnelState.from(activeTunnel)

                        TunnelNotificationLine(
                            id = id,
                            name = tunnel.name,
                            displayState = displayState,
                        )
                    }
                    .associateBy { it.id }
            }
            .distinctUntilChanged()
            .debounce(500.milliseconds) // give the service notification time to display
            .onEach { proxyLines ->
                notificationManager.updateProxyPersistentNotification(proxyLines)
            }
            .launchIn(scope)
    }

    private fun showOrNotify(
        scope: CoroutineScope,
        foregroundAction: suspend () -> Unit,
        backgroundAction: () -> Unit,
    ) {
        if (appVisibilityObserver.isForeground.value) {
            scope.launch { foregroundAction() }
        } else {
            backgroundAction()
        }
    }

    private suspend fun getTunnelName(tunnelId: Int): String {
        return tunnelRepository.getById(tunnelId)?.name ?: context.getString(R.string.unknown)
    }
}
