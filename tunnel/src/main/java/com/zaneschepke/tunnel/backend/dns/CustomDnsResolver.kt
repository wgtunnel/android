package com.zaneschepke.tunnel.backend.dns

import com.zaneschepke.tunnel.DnsConfigManager
import com.zaneschepke.tunnel.model.DnsBoostrapConfig
import com.zaneschepke.tunnel.model.DnsBootstrapResult

class CustomDnsResolver(private val dnsConfig: DnsBoostrapConfig, private val bypass: Boolean) :
    PeerResolver {

    override suspend fun resolve(host: String): DnsBootstrapResult {
        return DnsConfigManager.resolveHostBootstrap(
            host = host,
            protocol = dnsConfig.protocol,
            upstream = dnsConfig.upstream ?: DnsBoostrapConfig.DEFAULT_PLAIN_UPSTREAM,
            bypass = bypass,
        )
    }
}
