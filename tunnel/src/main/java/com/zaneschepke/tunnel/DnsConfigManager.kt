package com.zaneschepke.tunnel

import com.zaneschepke.tunnel.model.DnsBoostrapConfig
import com.zaneschepke.tunnel.model.DnsBootstrapResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object DnsConfigManager {

    private external fun resolveBootstrap(
        host: String,
        protocol: String,
        upstream: String,
        underlyingDnsServers: String,
        bypass: Int,
    ): String

    suspend fun resolveHostBootstrap(
        host: String,
        protocol: String,
        upstream: String,
        underlyingDnsServers: String = DnsBoostrapConfig.DEFAULT_UNDERLYING_SERVERS,
        bypass: Boolean,
    ): DnsBootstrapResult =
        withContext(Dispatchers.IO) {
            val bypassOption = if (bypass) 1 else 0
            val raw = resolveBootstrap(host, protocol, upstream, underlyingDnsServers, bypassOption)

            if (raw.startsWith("ERR|")) {
                throw RuntimeException(raw.removePrefix("ERR|"))
            }

            val parts = raw.split(";")

            val v4 =
                parts
                    .firstOrNull { it.startsWith("v4=") }
                    ?.removePrefix("v4=")
                    ?.takeIf { it.isNotBlank() }
                    ?.split(",") ?: emptyList()

            val v6 =
                parts
                    .firstOrNull { it.startsWith("v6=") }
                    ?.removePrefix("v6=")
                    ?.takeIf { it.isNotBlank() }
                    ?.split(",") ?: emptyList()

            DnsBootstrapResult(ipv4 = v4, ipv6 = v6)
        }
}
