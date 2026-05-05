package com.zaneschepke.wireguardautotunnel.util.network

import com.marsounjan.icmp4a.Icmp
import com.marsounjan.icmp4a.Icmp4a
import com.zaneschepke.wireguardautotunnel.util.extensions.round
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.io.IOException
import java.net.InetAddress
import java.time.Instant
import kotlin.math.sqrt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

enum class PingMethod {
    ICMP,      // ICMP ping (требует root или VPN)
    TCP,       // TCP connect ping
    HTTP_HEAD, // HTTP HEAD request
    HTTPS_GET  // HTTPS GET request с таймаутом
}

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
    var method: PingMethod = PingMethod.ICMP,
    var statusCode: Int? = null,
    var errorMessage: String? = null
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
    
    fun getQualityString(): String {
        return when {
            !isReachable -> "Недоступен"
            rttAvg < 50 -> "Отлично (${rttAvg.toInt()} мс)"
            rttAvg < 100 -> "Хорошо (${rttAvg.toInt()} мс)"
            rttAvg < 200 -> "Средне (${rttAvg.toInt()} мс)"
            else -> "Плохо (${rttAvg.toInt()} мс)"
        }
    }
}

class NetworkUtils(private val ioDispatcher: CoroutineDispatcher) {

    private val httpClient = HttpClient(CIO) {
        engine {
            connectTimeout = 5000
            socketTimeout = 5000
        }
    }

    /**
     * Performs a ping with stats using multiple methods (ICMP, TCP, HTTP).
     * Tries methods in order of preference and returns the best result.
     *
     * @param host The host to ping (domain, IPv4, or IPv6 address).
     * @param count Number of ping attempts.
     * @param timeoutMillis Overall timeout in milliseconds for the entire operation.
     * @param port TCP port for TCP ping (default: 443 for HTTPS, 80 for HTTP).
     * @param path HTTP path for HTTP HEAD/GET requests (default: "/").
     * @return PingStats with combined results from all methods.
     */
    suspend fun pingWithStats(
        host: String,
        count: Int,
        timeoutMillis: Long = (count * 2000L),
        port: Int = 443,
        path: String = "/"
    ): PingStats {
        return withTimeout(timeoutMillis) {
            withContext(ioDispatcher) {
                // Try ICMP first (best method if available)
                val icmpStats = try {
                    pingIcmp(host, count)
                } catch (e: Exception) {
                    Timber.w("ICMP ping failed: ${e.message}")
                    null
                }
                
                // If ICMP succeeded and shows reachability, return it
                if (icmpStats != null && icmpStats.isReachable) {
                    return@withContext icmpStats.copy(method = PingMethod.ICMP)
                }
                
                // Try TCP ping
                val tcpStats = try {
                    pingTcp(host, port, count)
                } catch (e: Exception) {
                    Timber.w("TCP ping failed: ${e.message}")
                    null
                }
                
                if (tcpStats != null && tcpStats.isReachable) {
                    return@withContext tcpStats.copy(method = PingMethod.TCP)
                }
                
                // Try HTTP HEAD
                val httpStats = try {
                    pingHttp(host, port, path, count, useGet = false)
                } catch (e: Exception) {
                    Timber.w("HTTP HEAD ping failed: ${e.message}")
                    null
                }
                
                if (httpStats != null && httpStats.isReachable) {
                    return@withContext httpStats.copy(method = PingMethod.HTTP_HEAD)
                }
                
                // Try HTTPS GET as last resort
                val httpsStats = try {
                    pingHttp(host, port, path, count, useGet = true)
                } catch (e: Exception) {
                    Timber.w("HTTPS GET ping failed: ${e.message}")
                    null
                }
                
                if (httpsStats != null && httpsStats.isReachable) {
                    return@withContext httpsStats.copy(method = PingMethod.HTTPS_GET)
                }
                
                // Return ICMP stats even if not reachable (for error info)
                icmpStats ?: PingStats().apply { 
                    errorMessage = "Все методы пинга не доступны"
                    isReachable = false
                }
            }
        }
    }
    
    /**
     * Performs ICMP ping with stats
     */
    private suspend fun pingIcmp(host: String, count: Int): PingStats {
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
                        Timber.w("ICMP Ping failed: ${result.message}")
                    }
                }
            }
            .catch {
                when (it) {
                    is CancellationException -> Timber.d("ICMP Ping completed")
                    else -> throw it
                }
            }
            .collect()

        return calculateStats(stats, count, received, rttList, lastSuccessTime)
    }
    
    /**
     * Performs TCP connection ping
     */
    private suspend fun pingTcp(host: String, port: Int, count: Int): PingStats {
        val stats = PingStats()
        val rttList = mutableListOf<Double>()
        var received = 0
        var lastSuccessTime: Long? = null
        
        repeat(count) {
            try {
                val startTime = System.currentTimeMillis()
                val socket = java.net.Socket()
                try {
                    socket.connect(java.net.InetSocketAddress(host, port), 2000)
                    val endTime = System.currentTimeMillis()
                    val rtt = endTime - startTime
                    rttList.add(rtt.toDouble())
                    received++
                    lastSuccessTime = Instant.now().toEpochMilli()
                } finally {
                    socket.close()
                }
            } catch (e: Exception) {
                Timber.w("TCP ping attempt $it failed: ${e.message}")
            }
            delay(500)
        }
        
        return calculateStats(stats, count, received, rttList, lastSuccessTime)
    }
    
    /**
     * Performs HTTP HEAD/GET request ping
     */
    private suspend fun pingHttp(host: String, port: Int, path: String, count: Int, useGet: Boolean): PingStats {
        val stats = PingStats()
        val rttList = mutableListOf<Double>()
        var received = 0
        var lastSuccessTime: Long? = null
        var lastStatusCode: Int? = null
        
        val scheme = if (port == 443 || port == 8443) "https" else "http"
        val url = "$scheme://$host:$port$path"
        
        repeat(count) {
            try {
                val startTime = System.currentTimeMillis()
                val response: HttpResponse = if (useGet) {
                    httpClient.get(url)
                } else {
                    httpClient.head(url)
                }
                val endTime = System.currentTimeMillis()
                val rtt = endTime - startTime
                
                if (response.status.value < 500) {
                    rttList.add(rtt.toDouble())
                    received++
                    lastSuccessTime = Instant.now().toEpochMilli()
                    lastStatusCode = response.status.value
                }
            } catch (e: Exception) {
                Timber.w("HTTP ping attempt $it failed: ${e.message}")
            }
            delay(500)
        }
        
        val result = calculateStats(stats, count, received, rttList, lastSuccessTime)
        result.statusCode = lastStatusCode
        return result
    }
    
    /**
     * Helper function to calculate ping statistics
     */
    private fun calculateStats(
        stats: PingStats,
        count: Int,
        received: Int,
        rttList: List<Double>,
        lastSuccessTime: Long?
    ): PingStats {
        if (rttList.isNotEmpty()) {
            stats.transmitted = count
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
        return stats
    }
}
