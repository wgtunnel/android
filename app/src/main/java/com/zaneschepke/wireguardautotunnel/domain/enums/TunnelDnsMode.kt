package com.zaneschepke.wireguardautotunnel.domain.enums

import android.content.Context
import com.zaneschepke.wireguardautotunnel.R

enum class TunnelDnsMode(val value: Int) {
    Off(0),
    Encrypted(1),
    Split(2),
    AllLocal(3);

    fun asString(context: Context): String {
        return when (this) {
            Off -> context.getString(R.string._default)
            Encrypted -> context.getString(R.string.encrypted_dns)
            Split -> context.getString(R.string.split_dns)
            AllLocal -> context.getString(R.string.system_dns_only)
        }
    }

    fun asDescription(context: Context): String {
        return when (this) {
            Off -> context.getString(R.string.default_dns_desc)
            Encrypted -> context.getString(R.string.encrypted_dns_desc)
            Split -> context.getString(R.string.split_dns_desc)
            AllLocal -> context.getString(R.string.system_dns_only_desc)
        }
    }

    companion object {
        fun fromValue(value: Int): TunnelDnsMode = entries.find { it.value == value } ?: Off
    }
}
