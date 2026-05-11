package com.zaneschepke.wireguardautotunnel.data.model

enum class TunnelMode(val value: Int) {
    VPN(0),
    PROXY(1),
    LOCK_DOWN(2);

    companion object {
        fun fromValue(value: Int): TunnelMode = entries.find { it.value == value } ?: VPN
    }
}
