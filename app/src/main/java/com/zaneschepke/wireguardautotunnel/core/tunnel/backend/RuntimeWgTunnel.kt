package com.zaneschepke.wireguardautotunnel.core.tunnel.backend

import com.wireguard.android.backend.Tunnel
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import kotlinx.coroutines.channels.Channel

class RuntimeWgTunnel(
    private val config: TunnelConfig,
    private val stateChannel: Channel<Tunnel.State>,
) : Tunnel {

    override fun getName() = config.name

    override fun onStateChange(newState: Tunnel.State) {
        stateChannel.trySend(newState)
    }

    override fun isIpv4ResolutionPreferred() = config.isIpv4Preferred
}
