package com.zaneschepke.wireguardautotunnel.domain.enums

enum class WifiDetectionMethod(val value: Int) {
    DEFAULT(0),
    LEGACY(1),
    ROOT(2),
    SHIZUKU(3);

    fun needsLocationPermissions(): Boolean {
        return this == LEGACY || this == DEFAULT
    }

    companion object {
        fun fromValue(value: Int): WifiDetectionMethod =
            entries.find { it.value == value } ?: DEFAULT
    }
}
