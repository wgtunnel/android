package com.zaneschepke.wireguardautotunnel.util.network

import android.content.Context
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DnsBlocker(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        val DEFAULT_BLOCKLISTS = listOf(
            "https://raw.githubusercontent.com/duckduckgo/duckduckgo-tracker-blocklist/master/trackerblocklist.txt",
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "https://raw.githubusercontent.com/nextdns/blocklists/master/ads.txt",
            "https://raw.githubusercontent.com/nextdns/blocklists/master/malware.txt",
            "https://raw.githubusercontent.com/nextdns/blocklists/master/trackers.txt"
        )

        val RUSSIAN_DNS_SERVERS = mapOf(
            "Astracat DNS" to listOf("45.153.198.137", "45.153.198.138"),
            "geohide DNS" to listOf("80.241.210.53", "80.241.210.54"),
            "Xbox DNS" to listOf("104.28.29.53", "104.28.29.54"),
            "mafioznik DNS" to listOf("185.185.128.185", "185.185.128.186"),
            "Yandex DNS" to listOf("77.88.8.8", "77.88.8.1")
        )
    }

    private val blockedDomains = ConcurrentHashMap<String, Boolean>()
    private var isEnabled = false

    fun initialize(enabled: Boolean = false) {
        this.isEnabled = enabled
        if (enabled) loadDefaultBlocklists()
    }

    private fun loadDefaultBlocklists() {
        val defaultBlocked = listOf(
            "doubleclick.net", "google-analytics.com", "googlesyndication.com",
            "facebook.com", "fbcdn.net", "instagram.com", "twitter.com",
            "adservice.google.com", "admob.com", "tracking.io"
        )
        defaultBlocked.forEach { domain ->
            blockedDomains[domain.lowercase()] = true
            blockedDomains["www.$domain".lowercase()] = true
        }
    }

    fun isBlocked(domain: String): Boolean {
        if (!isEnabled) return false
        val lowerDomain = domain.lowercase()
        if (blockedDomains.containsKey(lowerDomain)) return true
        return false
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun getRussianDnsOptions(): Map<String, List<String>> = RUSSIAN_DNS_SERVERS
}
