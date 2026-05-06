package com.zaneschepke.wireguardautotunnel.domain.model

import com.zaneschepke.wireguardautotunnel.util.network.NetworkUtils

data class MonitoringSettings(
    val id: Int = 0,
    val isPingEnabled: Boolean = false,
    val isPingMonitoringEnabled: Boolean = true,
    val tunnelPingIntervalSeconds: Int = 30,
    val tunnelPingAttempts: Int = 3,
    val tunnelPingTimeoutSeconds: Int? = null,
    val showDetailedPingStats: Boolean = false,
    val isLocalLogsEnabled: Boolean = false,

    // === НОВЫЕ НАСТРОЙКИ ===
    // Выбор метода пинга
    val pingMethod: NetworkUtils.PingMethod = NetworkUtils.PingMethod.ICMP,
    val tcpPingPort: Int = 443,  // Порт для TCP пинга
    val httpPingPath: String = "/",  // Путь для HTTP пинга

    // Настройки DNS блокировки
    val enableDnsBlocking: Boolean = false,
    val useDuckDuckGoBlocklist: Boolean = true,
    val customBlocklists: List<String> = listOf(
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "https://raw.githubusercontent.com/nextdns/blocklists/master/ads.txt"
    ),

    // Настройки LAN раздачи (Proxy Mode)
    val enableLanSharing: Boolean = false,
    val lanSocks5Port: Int = 1080,
    val lanHttpProxyPort: Int = 3128,
    val proxyAuthenticationEnabled: Boolean = false,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,

    // Настройки DNS серверов
    val useCustomDns: Boolean = false,
    val customDnsServers: List<String> = listOf(
        "1.1.1.1",       // Cloudflare
        "8.8.8.8",       // Google
        "77.88.8.8",     // Yandex
        "45.153.198.137", // Astracat
        "80.241.210.53",  // geohide
        "104.28.29.53",   // Xbox
        "185.185.128.185" // mafioznik
    )
)
