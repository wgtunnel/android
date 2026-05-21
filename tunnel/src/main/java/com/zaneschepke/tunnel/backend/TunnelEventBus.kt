package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.StatusCallback
import com.zaneschepke.tunnel.VpnBackend
import com.zaneschepke.tunnel.state.NativeTunnelStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object TunnelEventBus {

    private val channel = Channel<NativeTunnelStatus>(Channel.BUFFERED)
    val flow = channel.receiveAsFlow()

    private val callback = StatusCallback { handle, code ->
        val status = NativeTunnelStatus.NativeTunnelStatusCode.from(code) ?: return@StatusCallback

        channel.trySend(NativeTunnelStatus(handle = handle, code = status))
    }

    fun start() {
        VpnBackend.setStatusCallback(callback)
    }

    fun stop() {
        VpnBackend.setStatusCallback(null)
        channel.close()
    }
}
