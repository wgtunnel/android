package com.zaneschepke.tunnel

import timber.log.Timber

internal object VpnBackend {

    fun setStatusCallback(callback: StatusCallback?) {
        Timber.d("setStatusCallback called with ${if (callback != null) "callback" else "null"}")
        awgSetStatusCallback(callback)
    }

    private external fun awgSetStatusCallback(callback: StatusCallback?)

    external fun awgGetConfig(handle: Int): String?

    external fun awgGetSocketV4(handle: Int): Int

    external fun awgGetSocketV6(handle: Int): Int

    external fun awgTurnOff(handle: Int)

    external fun awgTurnOn(ifName: String, tunFd: Int, settings: String, uapiPath: String): Int

    external fun awgUpdateTunnelPeers(handle: Int, settings: String): Int

    external fun awgVersion(): String
}
