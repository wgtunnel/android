package com.zaneschepke.wireguardautotunnel.data.mapper

import com.zaneschepke.wireguardautotunnel.data.entity.DnsSettings as Entity
import com.zaneschepke.wireguardautotunnel.domain.model.DnsSettings as Domain

fun Entity.toDomain(): Domain =
    Domain(
        id = id,
        bootstrapDnsProtocol = bootstrapDnsProtocol,
        bootstrapDnsEndpoint = bootstrapDnsEndpoint,
        isGlobalTunnelConfigDnsEnabled = isGlobalTunnelConfigDnsEnabled,
        tunnelDnsMode = tunnelDnsMode,
        tunnelDnsProtocol = tunnelDnsProtocol,
        tunnelDnsEndpoint = tunnelDnsEndpoint,
        useTunnelDnsServersInSplit = useTunnelDnsServersInSplit,
        localSuffixes = localSuffixes,
    )

fun Domain.toEntity(): Entity =
    Entity(
        id = id,
        bootstrapDnsProtocol = bootstrapDnsProtocol,
        bootstrapDnsEndpoint = bootstrapDnsEndpoint,
        isGlobalTunnelConfigDnsEnabled = isGlobalTunnelConfigDnsEnabled,
        tunnelDnsMode = tunnelDnsMode,
        tunnelDnsProtocol = tunnelDnsProtocol,
        useTunnelDnsServersInSplit = useTunnelDnsServersInSplit,
        tunnelDnsEndpoint = tunnelDnsEndpoint,
        localSuffixes = localSuffixes,
    )
