package com.zaneschepke.tunnel.util

import android.net.TrafficStats
import java.io.IOException
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.SocketException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

object PortUtils {

    fun isPortAvailable(port: Int): Boolean {
        if (port !in 1..65_535) return false
        return try {
            ServerSocket(port).use { true }
        } catch (_: IOException) {
            false
        }
    }

    @Throws(IOException::class)
    fun getAvailableTcpPort(tag: Int): Int {
        TrafficStats.setThreadStatsTag(tag)

        try {
            ServerSocket(0).use {
                return it.localPort
            }
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }

    @Throws(BackendException::class)
    suspend fun waitForUdpPortAvailable(port: Int, timeoutMs: Long = 3000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isUdpPortAvailable(port)) return
            delay(50.milliseconds)
        }
        throw BackendException.ListenPortUnavailable(
            "UDP ListenPort $port is still in use after waiting $timeoutMs ms",
            port,
        )
    }

    private fun isUdpPortAvailable(port: Int): Boolean {
        if (port !in 1..65_535) return false
        return try {
            DatagramSocket(port).use { true }
        } catch (_: SocketException) {
            false
        }
    }
}
