package com.zaneschepke.tunnel

import androidx.annotation.Keep
import com.zaneschepke.tunnel.backend.SocketProtector
import timber.log.Timber

@Keep
internal object ProxyBackend {
    external fun awgStartProxy(ifName: String, config: String, uapiPath: String, bypass: Int): Int

    external fun awgUpdateProxyTunnelPeers(handle: Int, settings: String): Int

    external fun awgTurnProxyTunnelOff(handle: Int)

    external fun awgGetProxyConfig(handle: Int): String

    fun setSocketProtector(sp: SocketProtector?) {
        Timber.d("setSocketProtector called with ${if (sp != null) "protector" else "null"}")
        awgSetSocketProtector(sp)
    }

    private external fun awgSetSocketProtector(sp: SocketProtector?)
}
