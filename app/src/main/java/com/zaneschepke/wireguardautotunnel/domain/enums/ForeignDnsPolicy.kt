package com.zaneschepke.wireguardautotunnel.domain.enums

import android.content.Context
import com.wgtunnel.backend.model.dns.ForeignDnsPolicy as CoreForeignDnsPolicy
import com.zaneschepke.wireguardautotunnel.R

enum class ForeignDnsPolicy(val value: Int) {
    Redirect(0),
    Block(1),
    Allow(2);

    fun asString(context: Context): String =
        when (this) {
            Redirect -> context.getString(R.string.transit_dns_redirect)
            Block -> context.getString(R.string.transit_dns_block)
            Allow -> context.getString(R.string.transit_dns_allow)
        }

    fun toCore(): CoreForeignDnsPolicy =
        when (this) {
            Redirect -> CoreForeignDnsPolicy.REDIRECT
            Block -> CoreForeignDnsPolicy.BLOCK
            Allow -> CoreForeignDnsPolicy.ALLOW
        }

    companion object {
        fun fromValue(value: Int): ForeignDnsPolicy = entries.find { it.value == value } ?: Redirect
    }
}
