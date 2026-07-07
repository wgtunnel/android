package com.zaneschepke.wireguardautotunnel.data.mapper

import com.zaneschepke.wireguardautotunnel.data.entity.TunnelConfig as Entity
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig as Domain

fun Entity.toDomain(): Domain =
    Domain(
        id = id,
        name = name,
        tunnelNetworks = tunnelNetworks,
        isMobileDataTunnel = isMobileDataTunnel,
        isPrimaryTunnel = isPrimaryTunnel,
        quickConfig = quickConfig,
        dynamicDnsEnabled = dynamicDnsEnabled,
        isEthernetTunnel = isEthernetTunnel,
        isIpv6Preferred = isIpv6Preferred,
        position = position,
        autoTunnelApps = autoTunnelApps,
        isMetered = isMetered,
        ipv4FallbackEnabled = ipv4FallbackEnabled,
        ipv6RestoreEnabled = ipv6RestoreEnabled,
        tunnelBSSIDs = tunnelBSSIDs,
    )

fun Domain.toEntity(): Entity =
    Entity(
        id = id,
        name = name,
        tunnelNetworks = tunnelNetworks,
        isMobileDataTunnel = isMobileDataTunnel,
        isPrimaryTunnel = isPrimaryTunnel,
        quickConfig = quickConfig,
        dynamicDnsEnabled = dynamicDnsEnabled,
        isEthernetTunnel = isEthernetTunnel,
        isIpv6Preferred = isIpv6Preferred,
        position = position,
        autoTunnelApps = autoTunnelApps,
        isMetered = isMetered,
        ipv4FallbackEnabled = ipv4FallbackEnabled,
        ipv6RestoreEnabled = ipv6RestoreEnabled,
        tunnelBSSIDs = tunnelBSSIDs,
    )
