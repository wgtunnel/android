package com.zaneschepke.networkmonitor

import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities

sealed class TransportEvent {
    data class Available(
        val network: Network,
        val wifiDetectionMethod: AndroidNetworkMonitor.WifiDetectionMethod? = null,
    ) : TransportEvent()

    data class Lost(val network: Network) : TransportEvent()

    data class CapabilitiesChanged(
        val network: Network,
        val networkCapabilities: NetworkCapabilities,
        val wifiDetectionMethod: AndroidNetworkMonitor.WifiDetectionMethod? = null,
    ) : TransportEvent()

    data class Permissions(val permissions: com.zaneschepke.networkmonitor.Permissions) :
        TransportEvent()

    data object Unknown : TransportEvent()

    data class LinkPropertiesChanged(val network: Network, val linkProperties: LinkProperties) :
        TransportEvent()
}
