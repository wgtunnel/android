package com.zaneschepke.wireguardautotunnel.domain.enums

import android.content.Context
import com.zaneschepke.wireguardautotunnel.R

enum class TunnelDnsProtocol(val value: Int) {
    Doh(0),
    Dot(1),
    Plain(2);

    fun asString(context: Context): String {
        return when (this) {
            Doh -> context.getString(R.string.doh)
            Dot -> context.getString(R.string.dot)
            Plain -> context.getString(R.string.plain_dns)
        }
    }

    companion object {
        fun fromValue(value: Int): TunnelDnsProtocol = entries.find { it.value == value } ?: Doh
    }
}
