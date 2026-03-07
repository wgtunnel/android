package com.zaneschepke.wireguardautotunnel.core.tunnel.backend

import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import kotlinx.coroutines.channels.Channel
import org.amnezia.awg.backend.Tunnel

class RuntimeAwgTunnel(
    private val tunnelConfig: TunnelConfig,
    private val stateChannel: Channel<Tunnel.State>,
) : Tunnel {

    override fun getName() = tunnelConfig.name

    override fun onStateChange(newState: Tunnel.State) {
        stateChannel.trySend(newState)
    }

    override fun isIpv4ResolutionPreferred() = tunnelConfig.isIpv4Preferred

    override fun isMetered() = tunnelConfig.isMetered
}
