package com.zaneschepke.wireguardautotunnel.domain.model

enum class DnsProtocol {
    IPv4,       // Стандартный IPv4 (1.1.1.1)
    IPv6,       // IPv6 (2606:4700:4700::1111)
    DoH,        // DNS over HTTPS (https://dns.google/dns-query)
    DoT,        // DNS over TLS (dns.google)
    ODoH,       // Oblivious DoH (для приватности)
    DNSCrypt,   // Зашифрованный DNS
    QUIC        // DNS over QUIC
}

data class DnsServer(
    val name: String,
    val protocol: DnsProtocol,
    val address: String,
    val port: Int? = null,
    val isCustom: Boolean = false,
    val isSecure: Boolean = false,
    val description: String? = null,
    val country: String? = null
)
