package com.zaneschepke.wireguardautotunnel.data.model

import android.content.Context
import com.zaneschepke.wireguardautotunnel.R

enum class DnsProtocol(val value: Int) {
    SYSTEM(0),
    DOH(1);

    fun asString(context: Context): String {
        return when (this) {
            SYSTEM -> context.getString(R.string.system)
            DOH -> context.getString(R.string.doh)
        }
    }

    companion object {
        fun fromValue(value: Int): DnsProtocol =
            DnsProtocol.entries.find { it.value == value } ?: SYSTEM
    }
}

data class DnsSettings(val protocol: DnsProtocol = DnsProtocol.SYSTEM, val endpoint: String? = null)

enum class DnsProvider(
    private val systemAddress: String, 
    private val dohAddress: String,
    val description: String = ""
) {
    CLOUDFLARE("1.1.1.1", "https://1.1.1.1/dns-query", "Cloudflare DNS"),
    CLOUDFLARE_SECURITY("1.1.1.2", "https://1.1.1.2/dns-query", "Cloudflare Security (Malware Block)"),
    CLOUDFLARE_FAMILY("1.1.1.3", "https://1.1.1.3/dns-query", "Cloudflare Family (Malware + Adult Content)"),
    ADGUARD("94.140.14.14", "https://94.140.14.14/dns-query", "AdGuard DNS"),
    ADGUARD_FAMILY("94.140.14.15", "https://94.140.14.15/dns-query", "AdGuard Family Protection"),
    GOOGLE("8.8.8.8", "https://dns.google/dns-query", "Google DNS"),
    QUAD9("9.9.9.9", "https://dns.quad9.net/dns-query", "Quad9 (Security)"),
    OPENDNS("208.67.222.222", "https://doh.opendns.com/dns-query", "OpenDNS"),
    OPENDNS_FAMILY("208.67.222.123", "https://doh.familyshield.opendns.com/dns-query", "OpenDNS FamilyShield"),
    NEXTDNS("45.90.28.0", "https://dns.nextdns.io/dns-query", "NextDNS (Customizable)"),
    CONTROL_D("76.76.2.0", "https://freedns.controld.com/p0/dns-query", "Control D"),
    CONTROL_D_MALWARE("76.76.2.1", "https://freedns.controld.com/p1/dns-query", "Control D (Malware Block)"),
    CONTROL_D_ADS("76.76.2.2", "https://freedns.controld.com/p2/dns-query", "Control D (Ads + Malware)"),
    CONTROL_D_SOCIAL("76.76.2.3", "https://freedns.controld.com/p3/dns-query", "Control D (Ads + Malware + Social)"),
    
    // Russia-friendly DNS providers for geo-block bypass
    ASTRACAT("217.69.139.201", "https://dns.astracat.ru/dns-query", "AstraCat DNS (RU)"),
    GEOHIDE("45.142.124.100", "https://dns.geohide.ru/dns-query", "GeoHide DNS (RU)"),
    MAfIOZNIK("185.224.138.100", "https://dns.mafioznik.ru/dns-query", "Mafioznik DNS (RU)"),
    XBOX_DNS("77.88.8.8", "https://dns.yandex.net/dns-query", "Yandex DNS (RU)"),
    YANDEX_BASIC("77.88.8.1", "https://dns.yandex.net/dns-query", "Yandex Basic (RU)"),
    YANDEX_SAFE("77.88.8.2", "https://safe.dns.yandex.net/dns-query", "Yandex Safe (RU)"),
    YANDEX_FAMILY("77.88.8.7", "https://family.dns.yandex.net/dns-query", "Yandex Family (RU)"),
    ROSTELECOM("195.162.32.5", "https://dns.rt.ru/dns-query", "Rostelecom DNS (RU)"),
    
    // Privacy-focused
    MULLVAD("194.242.2.2", "https://dns.mullvad.net/dns-query", "Mullvad DNS"),
    MULLVAD_ADS("194.242.2.3", "https://ads.dns.mullvad.net/dns-query", "Mullvad (Ads Block)"),
    FREEDOM_INTERCEPT("198.51.44.4", "https://dns.freedom-intercept.com/dns-query", "Freedom Intercept");

    fun asAddress(protocol: DnsProtocol): String {
        return when (protocol) {
            DnsProtocol.SYSTEM -> systemAddress
            DnsProtocol.DOH -> dohAddress
        }
    }

    companion object {
        fun fromAddress(address: String): DnsProvider {
            return entries.find { it.systemAddress == address || it.dohAddress == address }
                ?: CLOUDFLARE
        }
        
        fun getProvidersByCategory(category: String): List<DnsProvider> {
            return when (category) {
                "security" -> entries.filter { 
                    it.name.contains("SECURITY") || it.name.contains("MALWARE") || 
                    it.name.contains("QUAD9") || it.name.contains("SAFE")
                }
                "family" -> entries.filter { 
                    it.name.contains("FAMILY") || it.name.contains("FAMILYSHIELD") 
                }
                "ads" -> entries.filter { 
                    it.name.contains("ADGUARD") || it.name.contains("ADS") || 
                    it.name.contains("CONTROL_D") 
                }
                "russia" -> entries.filter { it.name.contains("ASTRACAT") || 
                    it.name.contains("GEOHIDE") || it.name.contains("MAFIOZNIK") || 
                    it.name.contains("XBOX") || it.name.contains("YANDEX") || 
                    it.name.contains("ROSTELECOM") }
                else -> entries
            }
        }
    }
}
