package com.zaneschepke.networkmonitor

import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.zaneschepke.networkmonitor.model.LinkPropertiesSnapshot
import com.zaneschepke.networkmonitor.util.WifiSecurityType

data class ConnectivityState(
    val activeNetwork: ActiveNetwork,
    val cellularNetworks: Map<Network, NetworkCapabilities>,
    val locationPermissionsGranted: Boolean,
    val locationServicesEnabled: Boolean,
    val vpnState: VpnState,
    val effectiveDnsInfo: DnsInfo = DnsInfo(),
    val underlyingDnsInfo: DnsInfo = DnsInfo(),
    val hasIpv6: Boolean = false,
    val airplaneModeOn: Boolean = false,
) {

    fun hasUsableNetwork(): Boolean {
        if (!hasActiveNetwork()) return false

        return when (activeNetwork) {
            is ActiveNetwork.Cellular -> hasAnyUsableCellular()
            is ActiveNetwork.Wifi,
            is ActiveNetwork.Ethernet -> hasInternetCapability()
            is ActiveNetwork.Disconnected -> false
        }
    }

    fun hasAnyUsableCellular(): Boolean {
        if (cellularNetworks.isEmpty()) return false

        if (cellularNetworks.values.any { hasValidatedInternet(it) }) return true

        return cellularNetworks.values.any {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasNotSuspended(it)
        }
    }

    private fun hasValidatedInternet(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            hasNotSuspended(caps)
    }

    private fun hasInternetCapability(
        caps: NetworkCapabilities? = activeNetwork.capabilities
    ): Boolean {
        if (caps == null) return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun hasNotSuspended(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
    }

    fun hasActiveNetwork(): Boolean = activeNetwork !is ActiveNetwork.Disconnected

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
    abstract val capabilities: NetworkCapabilities?

    fun key(): String {
        return when (this) {
            is Wifi -> "wifi:${networkId}"
            is Cellular -> "cell:${network?.hashCode() ?: 0}"
            is Ethernet -> "eth:${network?.hashCode() ?: 0}"
            is Disconnected -> "none"
        }
    }

    fun key(bssidAware: Boolean): String {
        return when (val active = this) {
            is Wifi -> "wifi:${networkId}${active.bssid}"
            is Cellular -> "cell:${network?.hashCode() ?: 0}"
            is Ethernet -> "eth:${network?.hashCode() ?: 0}"
            is Disconnected -> "none"
        }
    }

    data class Disconnected(
        override val network: Network? = null,
        override val capabilities: NetworkCapabilities? = null,
    ) : ActiveNetwork()

    data class Wifi(
        val ssid: String,
        val bssid: String,
        val securityType: WifiSecurityType?,
        val networkId: String,
        override val network: Network?,
        override val capabilities: NetworkCapabilities? = null,
        val linkProperties: LinkPropertiesSnapshot = LinkPropertiesSnapshot(),
    ) : ActiveNetwork() {
        val requiresCaptivePortalLogin: Boolean
            get() =
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) ==
                    true
    }

    data class Cellular(
        override val network: Network?,
        override val capabilities: NetworkCapabilities? = null,
    ) : ActiveNetwork()

    data class Ethernet(
        override val network: Network?,
        override val capabilities: NetworkCapabilities? = null,
    ) : ActiveNetwork()
}

sealed interface VpnState {
    object Inactive : VpnState

    data class Active(val hasInternet: Boolean) : VpnState
}
