package com.zaneschepke.tunnel.features

import com.zaneschepke.pinger.Pinger
import com.zaneschepke.pinger.TcpConnectPinger
import com.zaneschepke.pinger.model.PingConfig
import com.zaneschepke.pinger.model.PingStats
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.model.BackendMode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.time.Duration.Companion.seconds

//class PingFeature(val pinger: Pinger = TcpConnectPinger()){
//
//    suspend fun monitor(
//        tunnelId: Int,
//        mode: BackendMode,
//        feature: Tunnel.Feature.PingMonitor,
//        statusUpdater: (Int, PingStats?) -> Unit
//    ) = coroutineScope {
//        val proxyConfig = (mode as? BackendMode.Proxy.Standard)?.proxyConfig
//
//        val proxy = when {
//            proxyConfig?.socks5 != null ->
//                Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyConfig.socks5.port))
//            proxyConfig?.http != null ->
//                Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyConfig.http.port))
//            else -> null
//        }
//
//        val proxyUsername = proxyConfig?.socks5?.username ?: proxyConfig?.http?.username
//        val proxyPassword = proxyConfig?.socks5?.password ?: proxyConfig?.http?.password
//
//        val pingConfig = PingConfig(
//            targetHost = feature.target ?: "1.1.1.1",
//            targetPort = 443,
//            count = feature.attempts,
//            timeoutMs = (feature.timeoutSeconds ?: 3) * 1000,
//            proxy = proxy,
//            proxyUsername = proxyUsername,
//            proxyPassword = proxyPassword,
//            delayBetweenPingsMs = 200L
//        )
//
//        while (isActive) {
//            try {
//                val stats = pinger.ping(pingConfig)
//                statusUpdater(tunnelId, stats)
//            } catch (e: Exception) {
//                Timber.e(e, "Ping failed for tunnel $tunnelId")
//                statusUpdater(tunnelId, null)
//            }
//            delay(feature.intervalSeconds.seconds)
//        }
//    }
//}