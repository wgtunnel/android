package com.zaneschepke.pinger.model

data class PingStats(
    var transmitted: Int = 0,
    var received: Int = 0,
    var packetLoss: Double = 0.0, // percentage
    var rttMin: Double = 0.0,
    var rttAvg: Double = 0.0,
    var rttMax: Double = 0.0,
    var rttStddev: Double = 0.0,
    var isReachable: Boolean = false,
    var lastSuccessfulPingMillis: Long? = null,
) {
    fun handleOffline(): PingStats {
        return copy(
            transmitted = 0,
            received = 0,
            packetLoss = 0.0,
            rttMin = 0.0,
            rttAvg = 0.0,
            rttMax = 0.0,
            rttStddev = 0.0,
            isReachable = false,
        )
    }
}
