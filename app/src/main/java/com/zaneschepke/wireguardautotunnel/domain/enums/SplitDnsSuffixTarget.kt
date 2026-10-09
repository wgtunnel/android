package com.zaneschepke.wireguardautotunnel.domain.enums

import android.content.Context
import com.wgtunnel.backend.model.dns.DnsSplitMode
import com.zaneschepke.wireguardautotunnel.R

enum class SplitDnsSuffixTarget(val value: Int) {
    System(0),
    Tunnel(1);

    fun asString(context: Context): String =
        when (this) {
            System -> context.getString(R.string.split_suffix_target_system)
            Tunnel -> context.getString(R.string.split_suffix_target_tunnel)
        }

    fun asDescription(context: Context): String =
        when (this) {
            System -> context.getString(R.string.split_suffix_target_desc_system)
            Tunnel -> context.getString(R.string.split_suffix_target_desc_tunnel)
        }

    fun toCore(): DnsSplitMode =
        when (this) {
            System -> DnsSplitMode.SYSTEM
            Tunnel -> DnsSplitMode.TUNNEL
        }

    companion object {
        fun fromValue(value: Int): SplitDnsSuffixTarget =
            entries.find { it.value == value } ?: System
    }
}
