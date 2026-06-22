package com.zaneschepke.tunnel

import androidx.annotation.Keep
import timber.log.Timber

@Keep
internal object VpnBackend {

    fun setStatusCallback(callback: StatusCallback?) {
        Timber.d("setStatusCallback called with ${if (callback != null) "callback" else "null"}")
        awgSetStatusCallback(callback)
    }

    private external fun awgSetStatusCallback(callback: StatusCallback?)

    external fun awgGetConfig(handle: Int): String?

    external fun awgTurnOff(handle: Int)

    external fun awgTriggerBindUpdate(handle: Int)

    external fun awgTurnOn(ifName: String, tunFd: Int, settings: String, uapiPath: String): Int

    external fun awgUpdateTunnelPeers(handle: Int, settings: String): Int

    external fun awgVersion(): String
}
