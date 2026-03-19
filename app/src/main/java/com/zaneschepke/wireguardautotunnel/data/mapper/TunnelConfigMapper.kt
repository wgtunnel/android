package com.zaneschepke.wireguardautotunnel.data.mapper

import com.zaneschepke.wireguardautotunnel.data.entity.TunnelConfig as Entity
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig as Domain

fun Entity.toDomain(): Domain =
    Domain(
        id = id,
        name = name,
        wgQuick = wgQuick,
        tunnelNetworks = tunnelNetworks,
        isMobileDataTunnel = isMobileDataTunnel,
        isPrimaryTunnel = isPrimaryTunnel,
        amQuick = amQuick,
        isActive = isActive,
        restartOnPingFailure = restartOnPingFailure,
        pingTarget = pingTarget,
        isEthernetTunnel = isEthernetTunnel,
        isIpv4Preferred = isIpv4Preferred,
        position = position,
        autoTunnelApps = autoTunnelApps,
        isMetered = isMetered,
        fallbackTunnelId = fallbackTunnelId,
    )

fun Domain.toEntity(): Entity =
    Entity(
        id = id,
        name = name,
        wgQuick = wgQuick,
        tunnelNetworks = tunnelNetworks,
        isMobileDataTunnel = isMobileDataTunnel,
        isPrimaryTunnel = isPrimaryTunnel,
        amQuick = amQuick,
        isActive = isActive,
        restartOnPingFailure = restartOnPingFailure,
        pingTarget = pingTarget,
        isEthernetTunnel = isEthernetTunnel,
        isIpv4Preferred = isIpv4Preferred,
        position = position,
        autoTunnelApps = autoTunnelApps,
        isMetered = isMetered,
        fallbackTunnelId = fallbackTunnelId,
    )
