package com.zaneschepke.networkmonitor

import android.content.Context
import android.provider.Settings

enum class PrivateDnsMode {
    OFF,
    AUTOMATIC,
    HOSTNAME,
}

fun Context.privateDnsMode(): PrivateDnsMode =
    when (Settings.Global.getString(contentResolver, "private_dns_mode")) {
        "hostname" -> PrivateDnsMode.HOSTNAME
        "opportunistic" -> PrivateDnsMode.AUTOMATIC
        else -> PrivateDnsMode.OFF
    }

data class DnsInfo(
    val servers: List<String> = emptyList(),
    val privateDnsMode: PrivateDnsMode = PrivateDnsMode.OFF,
    val privateDnsHostname: String? = null,
) {
    val isEmpty: Boolean
        get() = servers.isEmpty()

    override fun toString() =
        "DnsInfo(servers=$servers, mode=$privateDnsMode, hostname=$privateDnsHostname)"
}
