package com.zaneschepke.networkmonitor.util

/** Sticky system broadcast */
internal const val TETHER_STATE_CHANGED_ACTION = "android.net.conn.TETHER_STATE_CHANGED"

/** Extra on [TETHER_STATE_CHANGED_ACTION]: currently tethered iface names. */
internal const val EXTRA_ACTIVE_TETHER_IFACES = "tetherArray"

/** Extra on [TETHER_STATE_CHANGED_ACTION]: local-only (no forwarding) iface names. */
internal const val EXTRA_ACTIVE_LOCAL_ONLY_IFACES = "android.net.extra.ACTIVE_LOCAL_ONLY"

/**
 * Interface names used by the phone when it is the AP or P2P group owner, not a STA client. `wlan1`
 * is intentionally omitted: some devices use it as a second STA radio.
 */
internal fun interfaceNameLooksLikeSoftApOrP2p(name: String?): Boolean {
    val n = name?.lowercase() ?: return false
    return n.startsWith("ap_") ||
        n.startsWith("softap") ||
        n.startsWith("p2p") ||
        n.startsWith("swlan") ||
        n.startsWith("wlan-ap") ||
        n == "ap0"
}

/**
 * Wireless Android Auto is often a real associated STA (wlan0, real networkId/SSID) with no
 * INTERNET capability and no HEAD_UNIT/P2P flag. Those networks must still be ignored.
 */
internal fun looksLikeServedOrLocalWifi(
    interfaceName: String?,
    hasLocalNetwork: Boolean,
    hasHeadUnit: Boolean,
    hasWifiP2p: Boolean,
    hasInternet: Boolean,
    tetheredIfaces: Set<String>,
): Boolean {
    if (hasLocalNetwork || hasHeadUnit || hasWifiP2p) return true
    if (interfaceNameLooksLikeSoftApOrP2p(interfaceName)) return true
    if (!interfaceName.isNullOrEmpty() && interfaceName in tetheredIfaces) return true
    // Local-only STA (Wireless Android Auto, SoftAP named wlan0/wlan1, etc.)
    if (!hasInternet) return true
    return false
}
