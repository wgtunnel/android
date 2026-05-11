package com.zaneschepke.tunnel

import com.zaneschepke.tunnel.backend.SocketProtector
import timber.log.Timber

internal object ProxyBackend {
    external fun awgStartProxy(ifName: String, config: String, uapiPath: String, bypass: Int): Int

    external fun awgUpdateProxyTunnelPeers(handle: Int, settings: String): Int

    external fun awgStopProxy()

    external fun awgTurnProxyTunnelOff(handle: Int)

    external fun awgGetProxyConfig(handle: Int): String

    fun setSocketProtector(sp: SocketProtector?) {
        Timber.d("setSocketProtector called with ${if (sp != null) "protector" else "null"}")
        awgSetSocketProtector(sp)
    }

    private external fun awgSetSocketProtector(sp: SocketProtector?)
}
