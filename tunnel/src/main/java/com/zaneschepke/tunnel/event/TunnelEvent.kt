package com.zaneschepke.tunnel.event

import com.zaneschepke.tunnel.model.PublicKey

sealed interface TunnelEvent {

    // future runtime re-resolution
    data class DynamicDnsUpdate(val tunnelId: Int, val changedPeers: List<PublicKey>) : TunnelEvent

    data class FallbackToIpv4(val tunnelId: Int) : TunnelEvent

    data class RecoveredToIpv6(val tunnelId: Int) : TunnelEvent

    data class NoRootShellAccess(val tunnelId: Int) : TunnelEvent
}
