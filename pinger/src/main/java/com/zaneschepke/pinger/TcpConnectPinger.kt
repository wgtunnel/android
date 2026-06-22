package com.zaneschepke.pinger

import com.zaneschepke.pinger.model.PingConfig
import com.zaneschepke.pinger.model.PingStats
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TcpConnectPinger : Pinger {

    override suspend fun ping(config: PingConfig): PingStats =
        withContext(Dispatchers.IO) {
            val rtts = mutableListOf<Double>()
            var received = 0

            repeat(config.count) { i ->
                val start = System.currentTimeMillis()
                var socket: Socket? = null

                try {
                    socket = createSocket(config)
                    socket.soTimeout = config.timeoutMs

                    val address = InetSocketAddress(config.targetHost, config.targetPort)
                    socket.connect(address, config.timeoutMs)

                    val rtt = (System.currentTimeMillis() - start).toDouble()
                    rtts.add(rtt)
                    received++
                } catch (_: IOException) {
                    // timeout, refused, proxy issue so packet lost
                } finally {
                    socket?.close()
                }

                if (i < config.count - 1) {
                    kotlinx.coroutines.delay(config.delayBetweenPingsMs.milliseconds)
                }
            }

            val transmitted = config.count
            val loss =
                if (transmitted == 0) 0.0 else ((transmitted - received) * 100.0 / transmitted)

            if (rtts.isEmpty()) {
                return@withContext PingStats().handleOffline()
            }

            return@withContext PingStats(
                transmitted = transmitted,
                received = received,
                packetLoss = loss,
                rttMin = rtts.minOrNull() ?: 0.0,
                rttAvg = rtts.average(),
                rttMax = rtts.maxOrNull() ?: 0.0,
                rttStddev = calculateStdDev(rtts),
                isReachable = received > 0,
                lastSuccessfulPingMillis = System.currentTimeMillis(),
            )
        }

    private fun createSocket(config: PingConfig): Socket {
        return when {
            config.proxy?.type() == Proxy.Type.SOCKS &&
                config.proxyUsername != null &&
                config.proxyPassword != null -> {
                val addr = config.proxy.address() as InetSocketAddress
                Socks5SocketFactory(
                        proxyHost = addr.hostString,
                        proxyPort = addr.port,
                        username = config.proxyUsername,
                        password = config.proxyPassword,
                    )
                    .createSocket()
            }

            // no auth proxy
            else -> {
                config.proxy?.let { Socket(it) } ?: Socket()
            }
        }
    }

    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size <= 1) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean).pow(2) } / (values.size - 1)
        return sqrt(variance)
    }
}
