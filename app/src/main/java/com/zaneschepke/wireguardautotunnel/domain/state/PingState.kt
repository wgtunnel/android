package com.zaneschepke.wireguardautotunnel.domain.state

import com.zaneschepke.wireguardautotunnel.core.tunnel.handler.TunnelMonitorHandler.Companion.CLOUDFLARE_IPV4_IP

enum class FailureReason {
    NoConnectivity,
    PingFailed,
    NoResolvedEndpoint,
    Timeout,
    Unknown,
}

data class PingState(
    val transmitted: Int = 0,
    val received: Int = 0,
    val packetLoss: Double = 0.0,
    val rttMin: Double = 0.0,
    val rttMax: Double = 0.0,
    val rttAvg: Double = 0.0,
    val rttStddev: Double = 0.0,
    val isReachable: Boolean = false,
    val lastSuccessfulPingMillis: Long? = null,
    val lastPingAttemptMillis: Long? = null,
    val failureReason: FailureReason? = null,
    val pingTarget: String = CLOUDFLARE_IPV4_IP,
)
