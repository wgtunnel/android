package com.zaneschepke.wireguardautotunnel.domain.state

import com.zaneschepke.wireguardautotunnel.core.tunnel.handler.TunnelMonitorHandler.Companion.CLOUDFLARE_IPV4_IP
import com.zaneschepke.wireguardautotunnel.util.network.NetworkUtils

enum class FailureReason {
    NoConnectivity,
    PingFailed,
    NoResolvedEndpoint,
    Timeout,
    Unknown,
    DnsBlocked,      // НОВОЕ
    ProxyAuthFailed, // НОВОЕ
    PortBlocked      // НОВОЕ
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
    val pingMethod: String? = null  // НОВОЕ ПОЛЕ
)
