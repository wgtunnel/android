package com.zaneschepke.wireguardautotunnel.domain.model

data class DnsSettings(
    val id: Int = 0,
    val useCustomDns: Boolean = false,
    val customDnsServers: List<String> = emptyList(),

    // === НОВЫЕ ПОЛЯ ===
    // Поддерживаемые протоколы
    val supportedProtocols: Set<DnsProtocol> = setOf(DnsProtocol.IPv4, DnsProtocol.IPv6),

    // Выбранный протокол
    val selectedProtocol: DnsProtocol = DnsProtocol.IPv4,

    // Список DNS серверов с протоколами
    val dnsServers: List<DnsServer> = listOf(
        // IPv4 сервера
        DnsServer("Cloudflare IPv4", DnsProtocol.IPv4, "1.1.1.1", 53, false, true, "Быстрый и безопасный"),
        DnsServer("Google IPv4", DnsProtocol.IPv4, "8.8.8.8", 53, false, true, "Надежный"),
        DnsServer("Quad9 IPv4", DnsProtocol.IPv4, "9.9.9.9", 53, false, true, "С защитой от malware"),

        // IPv6 сервера
        DnsServer("Cloudflare IPv6", DnsProtocol.IPv6, "2606:4700:4700::1111", 53, false, true, "IPv6 от Cloudflare"),
        DnsServer("Google IPv6", DnsProtocol.IPv6, "2001:4860:4860::8888", 53, false, true, "IPv6 от Google"),

        // DoH сервера
        DnsServer("Cloudflare DoH", DnsProtocol.DoH, "https://dns.cloudflare.com/dns-query", null, false, true, "DNS over HTTPS"),
        DnsServer("Google DoH", DnsProtocol.DoH, "https://dns.google/dns-query", null, false, true, "DNS over HTTPS от Google"),
        DnsServer("Quad9 DoH", DnsProtocol.DoH, "https://dns.quad9.net/dns-query", null, false, true, "DoH с защитой"),

        // DoT сервера
        DnsServer("Cloudflare DoT", DnsProtocol.DoT, "1.1.1.1", 853, false, true, "DNS over TLS"),
        DnsServer("Google DoT", DnsProtocol.DoT, "8.8.8.8", 853, false, true, "DoT от Google"),
        DnsServer("Quad9 DoT", DnsProtocol.DoT, "9.9.9.9", 853, false, true, "DoT с защитой"),

        // ODoH сервера
        DnsServer("Cloudflare ODoH", DnsProtocol.ODoH, "https://odoh.cloudflare-dns.com", null, false, true, "Oblivious DoH"),

        // Русские DNS для обхода геоблока
        DnsServer("Astracat IPv4", DnsProtocol.IPv4, "45.153.198.137", 53, false, false, "Для обхода геоблока в РФ"),
        DnsServer("Astracat IPv6", DnsProtocol.IPv6, "2a0e:f9e0:4700:1::1", 53, false, false, "Astracat IPv6"),
        DnsServer("geohide IPv4", DnsProtocol.IPv4, "80.241.210.53", 53, false, false, "geohide для РФ"),
        DnsServer("Xbox IPv4", DnsProtocol.IPv4, "104.28.29.53", 53, false, false, "Microsoft DNS"),
        DnsServer("mafioznik IPv4", DnsProtocol.IPv4, "185.185.128.185", 53, false, false, "mafioznik DNS"),
        DnsServer("Yandex IPv4", DnsProtocol.IPv4, "77.88.8.8", 53, false, false, "Yandex DNS"),
        DnsServer("Yandex Safe IPv4", DnsProtocol.IPv4, "77.88.8.88", 53, false, false, "Yandex Safe DNS"),
    ),

    // Выбранный DNS сервер
    val selectedDnsServerIndex: Int = 0,

    // Настройки DoH/DoT
    val dohTemplate: String = "https://{server}/dns-query",
    val dotPort: Int = 853,

    // Включить автоматическое переключение при падении
    val enableFallback: Boolean = true,
    val fallbackServers: List<Int> = listOf(1, 2, 3) // Индексы серверов для фоллбэка
)
