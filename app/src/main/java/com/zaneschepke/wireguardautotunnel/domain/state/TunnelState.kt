// package com.zaneschepke.wireguardautotunnel.domain.state
//
// import com.zaneschepke.wireguardautotunnel.domain.enums.BackendMode
// import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelStatus
//
// data class TunnelState(
//    val status: TunnelStatus = TunnelStatus.Down,
//    val backendState: BackendMode = BackendMode.Inactive,
//    val statistics: TunnelStatistics? = null,
//    val pingStates: Map<String, PingState>? = null,
//    val logHealthState: LogHealthState? = null,
// ) {
//
//    fun health(): Health {
//        if (status !is TunnelStatus.Up) return Health.UNKNOWN
//        val uptime = uptime()
//        val now = System.currentTimeMillis()
//
//        if (pingStates == null && logHealthState == null && statistics == null)
//            return Health.UNKNOWN
//
//        // Logs check take precedent
//        logHealthState?.let { log ->
//            if (!log.isHealthy) return Health.UNHEALTHY
//            val recent = (now - log.timestamp) <= LOG_HEALTH_SUCCESS_TIMEOUT_MS
//            if (recent) {
//                // Logs healthy but override if pings are unhealthy
//                if (pingStates?.any { !it.value.isReachable } == true) return Health.UNHEALTHY
//                return Health.HEALTHY
//            }
//        }
//
//        // Ping health if no logs
//        pingStates?.let { pings ->
//            if (pings.any { !it.value.isReachable }) return Health.UNHEALTHY
//            return Health.HEALTHY
//        }
//
//        // Stats health if no logs or pings
//        statistics?.let { stats ->
//            if (stats.isTunnelStale()) return Health.STALE
//            val rx = stats.rx()
//            if (uptime >= STATS_HEALTH_SUCCESS_TIMEOUT_MS && rx == 0L) return Health.UNHEALTHY
//            if (rx == 0L) return Health.UNKNOWN
//            return Health.HEALTHY
//        }
//
//        return Health.UNKNOWN
//    }
//
//    fun uptime(): Long {
//        val up = status as? TunnelStatus.Up ?: return 0L
//        if (up.startTime == 0L) return 0L
//        return System.currentTimeMillis() - up.startTime
//    }
//
//    enum class Health {
//        UNKNOWN,
//        UNHEALTHY,
//        HEALTHY,
//        STALE,
//    }
//
//    companion object {
//        const val LOG_HEALTH_SUCCESS_TIMEOUT_MS = 2 * 60 * 1000L // 2 minutes
//        const val STATS_HEALTH_SUCCESS_TIMEOUT_MS = 15 * 1000L // 15 sec
//    }
// }
