package com.zaneschepke.wireguardautotunnel.util.network

import com.marsounjan.icmp4a.Icmp
import com.marsounjan.icmp4a.Icmp4a
import com.zaneschepke.wireguardautotunnel.util.extensions.round
import java.io.IOException
import java.time.Instant
import kotlin.math.sqrt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

class NetworkUtils(private val ioDispatcher: CoroutineDispatcher) {

    /**
     * Performs a ping with stats, wrapped in a coroutine for async execution. Dynamically handles
     * IPv4/ICMP or IPv6/ICMPv6 based on the host.
     *
     * @param host The host to ping (domain, IPv4, or IPv6 address).
     * @param count Number of ping attempts.
     * @param timeoutMillis Overall timeout in milliseconds for the entire operation.
     * @return PingStats if successful, with isReachable set based on whether any packets were
     *   received, and lastSuccessfulPingMillis set to the approximate epoch millis of the last
     *   successful ping response.
     * @throws IOException on failure (e.g., unknown host or other errors).
     * @throws TimeoutCancellationException on timeout.
     */
    suspend fun pingWithStats(
        host: String,
        count: Int,
        timeoutMillis: Long = (count * 2000L),
    ): PingStats {
        return withTimeout(timeoutMillis) {
            withContext(ioDispatcher) {
                val icmp = Icmp4a()
                val stats = PingStats()
                val rttList = mutableListOf<Double>()
                var received = 0
                var lastSuccessTime: Long? = null

                icmp
                    .pingInterval(host, count = count, intervalMillis = 500)
                    .onEach { status ->
                        when (val result = status.result) {
                            is Icmp.PingResult.Success -> {
                                received++
                                rttList.add(result.ms.toDouble())
                                lastSuccessTime = Instant.now().toEpochMilli()
                            }
                            is Icmp.PingResult.Failed -> {
                                Timber.w("Ping failed with result: ${result.message}")
                            }
                        }
                    }
                    .catch {
                        when (it) {
                            is CancellationException -> Timber.d("Ping completed")
                            else -> throw it
                        }
                    }
                    .collect()

                stats.transmitted = count
                if (rttList.isNotEmpty()) {
                    stats.received = received
                    stats.packetLoss = ((count - received).toDouble().round(2) / count) * 100
                    stats.rttMin = rttList.minOrNull()?.round(2) ?: 0.0
                    stats.rttAvg = rttList.average().round(2)
                    stats.rttMax = rttList.maxOrNull()?.round(2) ?: 0.0
                    val mean = stats.rttAvg
                    stats.rttStddev =
                        sqrt(rttList.map { (it - mean) * (it - mean) }.average()).round(2)
                    stats.isReachable = received > 0
                    stats.lastSuccessfulPingMillis = lastSuccessTime
                } else {
                    stats.isReachable = false
                }
                stats
            }
        }
    }
}
