package com.zaneschepke.wireguardautotunnel.domain.state

import com.zaneschepke.networkmonitor.ActiveNetwork as MonitorActiveNetwork
import com.zaneschepke.networkmonitor.ConnectivityState
import com.zaneschepke.networkmonitor.util.WifiSecurityType

sealed class ActiveNetwork {
    data object Disconnected : ActiveNetwork()

    data object Ethernet : ActiveNetwork()

    data object Cellular : ActiveNetwork()

    data class Wifi(val ssid: String, val isSecure: Boolean?, val bssid: String? = null) :
        ActiveNetwork()
}

data class NetworkState(
    val activeNetwork: ActiveNetwork = ActiveNetwork.Disconnected,
    val locationServicesEnabled: Boolean = false,
    val locationPermissionGranted: Boolean = false,
) {
    fun hasInternet(): Boolean = activeNetwork !is ActiveNetwork.Disconnected
}

fun ConnectivityState.toDomain(): NetworkState {
    val domainNetwork: ActiveNetwork =
        when (val network = this.activeNetwork) {
            is MonitorActiveNetwork.Wifi -> {
                val isSecure =
                    when (network.securityType) {
                        WifiSecurityType.OPEN,
                        WifiSecurityType.UNKNOWN -> false
                        null -> null
                        else -> true
                    }
                ActiveNetwork.Wifi(ssid = network.ssid, isSecure = isSecure, bssid = network.bssid)
            }
            is MonitorActiveNetwork.Cellular -> ActiveNetwork.Cellular
            is MonitorActiveNetwork.Ethernet -> ActiveNetwork.Ethernet
            is MonitorActiveNetwork.Disconnected -> ActiveNetwork.Disconnected
        }

    return NetworkState(
        activeNetwork = domainNetwork,
        locationPermissionGranted = this.locationPermissionsGranted,
        locationServicesEnabled = this.locationServicesEnabled,
    )
}
