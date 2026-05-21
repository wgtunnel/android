package com.zaneschepke.wireguardautotunnel.domain.enums

import android.content.Context
import com.zaneschepke.wireguardautotunnel.R

enum class StatisticRefresh(val value: Int) {
    LIVE(1),
    BALANCED(3),
    BATTERY_SAVER(10);

    fun asString(context: Context): String {
        return when (this) {
            LIVE -> context.getString(R.string.live)
            BALANCED -> context.getString(R.string.balanced)
            BATTERY_SAVER -> context.getString(R.string.balance_saver)
        }
    }

    companion object {
        fun fromValue(value: Int): StatisticRefresh = entries.find { it.value == value } ?: BALANCED
    }
}
