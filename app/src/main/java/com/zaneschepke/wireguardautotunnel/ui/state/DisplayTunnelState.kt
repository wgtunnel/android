package com.zaneschepke.wireguardautotunnel.ui.state

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.tunnel.state.BootstrapState
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.theme.AlertRed
import com.zaneschepke.wireguardautotunnel.ui.theme.CoolGray
import com.zaneschepke.wireguardautotunnel.ui.theme.SilverTree
import com.zaneschepke.wireguardautotunnel.ui.theme.Straw

sealed class DisplayTunnelState {
    data object Connecting : DisplayTunnelState()

    data object ResolvingDns : DisplayTunnelState()

    data object Connected : DisplayTunnelState()

    data object Ready : DisplayTunnelState()

    data object Degraded : DisplayTunnelState()

    data object Disconnected : DisplayTunnelState()

    @StringRes
    fun labelRes(): Int {
        return when (this) {
            ResolvingDns -> R.string.tunnel_state_resolving_dns
            Connecting -> R.string.tunnel_state_starting
            Connected -> R.string.tunnel_state_connected
            Degraded -> R.string.tunnel_state_handshake_failure
            Disconnected -> R.string.tunnel_state_disconnected
            Ready -> R.string.ready
        }
    }

    fun asLocalizedString(context: Context): String {
        return context.getString(this.labelRes())
    }

    fun asColor(): Color {
        return when (this) {
            Disconnected -> CoolGray
            Connecting,
            Ready,
            ResolvingDns -> Straw
            Degraded -> AlertRed
            Connected -> SilverTree
        }
    }

    companion object {
        fun from(activeTunnel: ActiveTunnel): DisplayTunnelState {
            val transport = activeTunnel.transportState
            val bootstrap = activeTunnel.bootstrapState

            return when {
                transport is Tunnel.State.Down -> Disconnected

                (bootstrap is BootstrapState.Complete &&
                    !activeTunnel.isPeerUpdating &&
                    transport is Tunnel.State.Starting) ||
                    bootstrap is BootstrapState.None && transport is Tunnel.State.Starting -> Ready

                bootstrap is BootstrapState.ResolvingDns ||
                    (bootstrap is BootstrapState.Complete && activeTunnel.isPeerUpdating) ->
                    ResolvingDns

                transport is Tunnel.State.Starting -> Connecting

                transport is Tunnel.State.Up.Healthy -> Connected

                transport is Tunnel.State.Up.HandshakeFailure && !activeTunnel.isPeerUpdating ->
                    Degraded

                else -> Connecting
            }
        }
    }
}
