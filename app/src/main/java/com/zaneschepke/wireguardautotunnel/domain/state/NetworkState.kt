package com.zaneschepke.wireguardautotunnel.domain.state

import com.zaneschepke.networkmonitor.ActiveNetwork
import com.zaneschepke.networkmonitor.ConnectivityState

data class NetworkState(
    val activeNetwork: ActiveNetwork = ActiveNetwork.Disconnected(),
    val locationServicesEnabled: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    // Has a network that can actually transfer data (not suspended)
    val hasUsableNetwork: Boolean = false,
)

fun ConnectivityState.toDomain(): NetworkState {

    return NetworkState(
        activeNetwork = activeNetwork,
        locationPermissionGranted = this.locationPermissionsGranted,
        locationServicesEnabled = this.locationServicesEnabled,
        hasUsableNetwork = hasUsableNetwork(),
    )
}
