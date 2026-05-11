package com.zaneschepke.wireguardautotunnel.data.model

import android.content.Context
import com.zaneschepke.wireguardautotunnel.R

enum class DnsProtocol(val value: Int) {
    SYSTEM(0),
    DOH(1),
    DOT(2),
    UDP(3);

    fun asString(context: Context): String {
        return when (this) {
            SYSTEM -> context.getString(R.string.system)
            DOH -> context.getString(R.string.doh)
            DOT -> context.getString(R.string.dot)
            UDP -> context.getString(R.string.plain_dns)
        }
    }

    companion object {
        fun fromValue(value: Int): DnsProtocol =
            DnsProtocol.entries.find { it.value == value } ?: SYSTEM
    }
}

data class DnsSettings(val protocol: DnsProtocol = DnsProtocol.SYSTEM, val endpoint: String? = null)
