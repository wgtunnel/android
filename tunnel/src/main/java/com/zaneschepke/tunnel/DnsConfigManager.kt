package com.zaneschepke.tunnel

import com.zaneschepke.tunnel.model.DnsBootstrapResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal object DnsConfigManager {
    private external fun setDNSConfig(configJson: String)

    fun update(protocol: String, upstream: String) {
        val config =
            JSONObject().apply {
                put("protocol", protocol)
                put("upstream", upstream)
            }
        setDNSConfig(config.toString())
    }

    private external fun resolveBootstrap(host: String, bypass: Int): String

    suspend fun resolveHostBootstrap(host: String, bypass: Boolean): DnsBootstrapResult =
        withContext(Dispatchers.IO) {
            val raw = resolveBootstrap(host, if (bypass) 1 else 0)

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
