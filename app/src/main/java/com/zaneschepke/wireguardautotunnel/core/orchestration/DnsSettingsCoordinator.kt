package com.zaneschepke.wireguardautotunnel.core.orchestration

import com.zaneschepke.tunnel.backend.Backend
import com.zaneschepke.tunnel.model.DnsBoostrapConfig
import com.zaneschepke.tunnel.model.DnsBoostrapMode
import com.zaneschepke.wireguardautotunnel.domain.enums.DnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.model.DnsSettings

class DnsSettingsCoordinator(private val backend: Backend) {

    suspend fun appyDnsSettings(dnsSettings: DnsSettings) {
        val mode =
            when (dnsSettings.dnsProtocol) {
                DnsProtocol.SYSTEM -> DnsBoostrapMode.System
                DnsProtocol.DOH ->
                    DnsBoostrapMode.Custom(DnsBoostrapConfig.DoH(dnsSettings.dnsEndpoint))
                DnsProtocol.DOT ->
                    DnsBoostrapMode.Custom(DnsBoostrapConfig.DoT(dnsSettings.dnsEndpoint))
                DnsProtocol.UDP ->
                    DnsBoostrapMode.Custom(DnsBoostrapConfig.Plain(dnsSettings.dnsEndpoint))
            }

        backend.setBootstrapDnsMode(mode)
    }
}
