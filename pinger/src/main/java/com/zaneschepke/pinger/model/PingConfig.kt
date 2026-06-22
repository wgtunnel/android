package com.zaneschepke.pinger.model

import java.net.Proxy

data class PingConfig(
    val targetHost: String = "1.1.1.1",
    val targetPort: Int = 443,
    val count: Int = 4,
    val timeoutMs: Int = 3000,
    val proxy: Proxy? = null,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,
    val delayBetweenPingsMs: Long = 200L,
)
