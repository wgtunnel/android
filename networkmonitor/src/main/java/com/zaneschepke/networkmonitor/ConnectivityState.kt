package com.zaneschepke.networkmonitor

import android.net.Network
import com.zaneschepke.networkmonitor.util.WifiSecurityType

data class ConnectivityState(
    val activeNetwork: ActiveNetwork,
    val locationPermissionsGranted: Boolean,
    val locationServicesEnabled: Boolean,
    val vpnState: VpnState,
    val effectiveDnsInfo: DnsInfo = DnsInfo(),
    val underlyingDnsInfo: DnsInfo = DnsInfo(),
    val hasIpv6: Boolean = false,
) {
    fun hasInternet(): Boolean = activeNetwork !is ActiveNetwork.Disconnected

    override fun toString(): String {
        val networkInfo =
            when (activeNetwork) {
                is ActiveNetwork.Disconnected -> "Disconnected"
                is ActiveNetwork.Ethernet -> "Ethernet"
                is ActiveNetwork.Cellular -> "Cellular"
                is ActiveNetwork.Wifi -> {
                    val ssidDisplay =
                        if (activeNetwork.ssid == AndroidNetworkMonitor.ANDROID_UNKNOWN_SSID)
                            activeNetwork.ssid
                        else activeNetwork.ssid.first() + "..."
                    "Wifi(ssid=$ssidDisplay, securityType=${activeNetwork.securityType})"
                }
            }
        return "activeNetwork=$networkInfo, locationPermissionsGranted=$locationPermissionsGranted, locationServicesEnabled=$locationServicesEnabled"
    }
}

data class Permissions(val locationServicesEnabled: Boolean, val locationPermissionGranted: Boolean)

sealed class ActiveNetwork {
    abstract val network: Network?

    fun key(): String {
        return when (this) {
            is Wifi -> "wifi:${networkId}"
            is Cellular -> "cell:${network?.hashCode() ?: 0}"
            is Ethernet -> "eth:${network?.hashCode() ?: 0}"
            is Disconnected -> "none"
        }
    }

    data class Disconnected(override val network: Network? = null) : ActiveNetwork()

    data class Wifi(
        val ssid: String,
        val securityType: WifiSecurityType?,
        val networkId: String,
        override val network: Network?,
    ) : ActiveNetwork()

    data class Cellular(override val network: Network?) : ActiveNetwork()

    data class Ethernet(override val network: Network?) : ActiveNetwork()
}

sealed interface VpnState {
    object Inactive : VpnState

    data class Active(val hasInternet: Boolean) : VpnState
}
