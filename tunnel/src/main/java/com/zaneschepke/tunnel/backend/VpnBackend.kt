package com.zaneschepke.tunnel.backend

import androidx.annotation.Keep

@Keep
internal object VpnBackend {
    external fun awgGetConfig(handle: Int): String?

    external fun awgTurnOff(handle: Int)

    external fun awgTurnOn(
        ifName: String,
        tunFd: Int,
        settings: String,
        uapiPath: String,
        splitDnsDomains: String,
        splitDnsSystemServers: String,
    ): Int

    external fun awgSetSplitDnsServers(handle: Int, servers: String): Int

    external fun awgUpdateTunnelPeers(handle: Int, settings: String): Int

    external fun awgVersion(): String
}
