package com.zaneschepke.wireguardautotunnel.domain.model

import com.zaneschepke.wireguardautotunnel.domain.enums.WifiDetectionMethod

data class AutoTunnelSettings(
    val id: Int = 0,
    val isAutoTunnelEnabled: Boolean = false,
    val isTunnelOnMobileDataEnabled: Boolean = false,
    val trustedNetworkSSIDs: List<String> = emptyList(),
    val isTunnelOnEthernetEnabled: Boolean = false,
    val isTunnelOnWifiEnabled: Boolean = false,
    val isWildcardsEnabled: Boolean = false,
    val isStopOnNoInternetEnabled: Boolean = false,
    val isTunnelOnUnsecureEnabled: Boolean = false,
    val wifiDetectionMethod: WifiDetectionMethod = WifiDetectionMethod.fromValue(0),
    val startOnBoot: Boolean = false,
    val disableTunnelOnCaptivePortal: Boolean = true,
    val trustedNetworkBSSIDs: List<String> = emptyList(),
)
