package com.zaneschepke.wireguardautotunnel.ui.state

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.tunnel.state.BootstrapState
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.theme.AlertRed
import com.zaneschepke.wireguardautotunnel.ui.theme.CoolGray
import com.zaneschepke.wireguardautotunnel.ui.theme.SilverTree
import com.zaneschepke.wireguardautotunnel.ui.theme.Straw

sealed class DisplayTunnelState {
    data object Disconnected : DisplayTunnelState()

    data object Connecting : DisplayTunnelState()

    data object ResolvingDns : DisplayTunnelState()

    data object EstablishingConnection : DisplayTunnelState()

    data object Ready : DisplayTunnelState()

    data object Connected : DisplayTunnelState()

    data object HandshakeFailure : DisplayTunnelState()

    @StringRes
    fun labelRes(): Int =
        when (this) {
            Disconnected -> R.string.tunnel_state_disconnected
            Connecting,
            EstablishingConnection -> R.string.tunnel_state_establishing_connection
            ResolvingDns -> R.string.tunnel_state_resolving_dns
            Ready -> R.string.ready
            Connected -> R.string.tunnel_state_connected
            HandshakeFailure -> R.string.tunnel_state_handshake_failure
        }

    fun asColor(): Color =
        when (this) {
            Disconnected -> CoolGray
            Connecting,
            ResolvingDns,
            EstablishingConnection,
            Ready -> Straw
            Connected -> SilverTree
            HandshakeFailure -> AlertRed
        }

    fun asLocalizedString(context: Context): String {
        return context.getString(labelRes())
    }

    companion object {
        fun from(activeTunnel: ActiveTunnel): DisplayTunnelState {
            val transport = activeTunnel.transportState
            val bootstrap = activeTunnel.bootstrapState
            val mode = activeTunnel.mode
            val isVpnStyle = mode is BackendMode.Vpn || mode is BackendMode.Proxy.KillSwitchPrimary

            return when {
                transport is Tunnel.State.Down -> Disconnected
                bootstrap is BootstrapState.Failed -> HandshakeFailure

                bootstrap is BootstrapState.ResolvingDns ||
                    bootstrap is BootstrapState.UpdatingPeers -> ResolvingDns

                transport is Tunnel.State.Up.Healthy -> Connected

                transport is Tunnel.State.Up.HandshakeFailure -> HandshakeFailure

                transport is Tunnel.State.Starting ->
                    if (isVpnStyle) EstablishingConnection else Ready

                else -> if (isVpnStyle) EstablishingConnection else Ready
            }
        }
    }
}
