package com.zaneschepke.networkmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor.WifiDetectionMethod.DEFAULT
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor.WifiDetectionMethod.LEGACY
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor.WifiDetectionMethod.ROOT
import com.zaneschepke.networkmonitor.AndroidNetworkMonitor.WifiDetectionMethod.SHIZUKU
import com.zaneschepke.networkmonitor.model.LinkPropertiesSnapshot
import com.zaneschepke.networkmonitor.shizuku.ShizukuShell
import com.zaneschepke.networkmonitor.util.getLegacySecurityType
import com.zaneschepke.networkmonitor.util.getWifiSecurityType
import com.zaneschepke.networkmonitor.util.getWifiSsidAndBssid
import com.zaneschepke.networkmonitor.util.hasRequiredLocationPermissions
import com.zaneschepke.networkmonitor.util.isAirplaneModeOn
import com.zaneschepke.networkmonitor.util.isLocationServicesEnabled
import java.net.Inet6Address
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class AndroidNetworkMonitor(
    private val appContext: Context,
    private val configurationListener: ConfigurationListener,
    private val applicationScope: CoroutineScope,
) : NetworkMonitor {

    private val actionPermissionCheck = "${appContext.packageName}.PERMISSION_CHECK"

    interface ConfigurationListener {
        val detectionMethod: Flow<WifiDetectionMethod>

        // maybe this shouldn't just be a string result
        suspend fun runRootShellCommand(cmd: String): String?
    }

    companion object {
        const val LOCATION_SERVICES_FILTER: String = "android.location.PROVIDERS_CHANGED"
        const val ANDROID_UNKNOWN_SSID: String = "<unknown ssid>"
        const val ANDROID_UNKNOWN_BSSID: String = "02:00:00:00:00:00"
        const val WIFI_INFO_SHELL_COMMAND = "cmd wifi status"
        const val SHELL_COMMAND_TIMEOUT_MS = 2_000L
    }

    enum class WifiDetectionMethod(val value: Int) {
        DEFAULT(0),
        LEGACY(1),
        ROOT(2),
        SHIZUKU(3);

        companion object {
            fun fromValue(value: Int): WifiDetectionMethod =
                entries.find { it.value == value } ?: DEFAULT
        }

        fun needsLocationPermissions(): Boolean {
            return this == DEFAULT || this == LEGACY
        }
    }

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager?

    private val permissionsChangedFlow = MutableStateFlow(false)

    private val airplaneModeState = MutableStateFlow(appContext.isAirplaneModeOn())
    private val activeCellularNetworks =
        MutableStateFlow<Map<Network, NetworkCapabilities>>(emptyMap())

    private val permissionCheckFlow: Flow<Unit> = callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == actionPermissionCheck) {
                        val isGranted = appContext.hasRequiredLocationPermissions()
                        Timber.d("Received permission check broadcast, isGranted: $isGranted")

                        if (
                            connectivityStateFlow.replayCache
                                .firstOrNull()
                                ?.locationPermissionsGranted != isGranted
                        ) {
                            Timber.d("Location permissions changed, restarting flows")
                            permissionsChangedFlow.update { !permissionsChangedFlow.value }
                        }
                    }
                }
            }

        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_NOT_EXPORTED
            } else 0

        appContext.registerReceiver(receiver, IntentFilter(actionPermissionCheck), flags)
        awaitClose { appContext.unregisterReceiver(receiver) }
    }

    private val locationServicesFlow: Flow<Unit> = callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == LOCATION_SERVICES_FILTER) {
                        val enabled = locationManager?.isLocationServicesEnabled() ?: false
                        Timber.d("Location services changed: $enabled")

                        if (
                            connectivityStateFlow.replayCache
                                .firstOrNull()
                                ?.locationServicesEnabled != enabled
                        ) {
                            Timber.d("Location services changed, restarting flows")
                            permissionsChangedFlow.update { !permissionsChangedFlow.value }
                        }
                    }
                }
            }

        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_EXPORTED
            } else 0

        appContext.registerReceiver(receiver, IntentFilter(LOCATION_SERVICES_FILTER), flags)
        awaitClose { appContext.unregisterReceiver(receiver) }
    }

    private val airplaneModeReceiverFlow: Flow<Boolean> = callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                        val isOn = intent.getBooleanExtra("state", false)
                        Timber.d("Airplane mode changed: $isOn")
                        if (isOn) activeCellularNetworks.value = emptyMap()
                        airplaneModeState.update { isOn }
                    }
                }
            }

        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_EXPORTED
            } else 0

        appContext.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
            flags,
        )
        awaitClose { appContext.unregisterReceiver(receiver) }
    }

    init {
        applicationScope.launch { permissionCheckFlow.collect() }
        applicationScope.launch { locationServicesFlow.collect() }
        applicationScope.launch { airplaneModeReceiverFlow.collect() }

        // Set initial airplane mode state
        airplaneModeState.update { appContext.isAirplaneModeOn() }
    }

    // tracking to prevent races that occur when VPN is first activated and to prevent redundant
    // location queries in Legacy mode
    private val lastKnownActiveNetwork =
        MutableStateFlow<ActiveNetwork>(ActiveNetwork.Disconnected())

    private data class WifiDetails(val ssid: String, val bssid: String)

    private fun parseWifiStatusOutput(output: String): WifiDetails {
        val ssidRegex = Regex("""(?i)connected to\s*"([^"]+)"""")
        val bssidRegex = Regex("""(?i)BSSID:\s*([a-fA-F0-9:]{17})""")

        val ssidMatch = ssidRegex.find(output)
        val bssidMatch = bssidRegex.find(output)

        val ssid =
            ssidMatch
                ?.groupValues
                ?.get(1)
                // Removes all whitespaces and newlines
                ?.trim { it.isWhitespace() || it == '\\' }
                ?.ifBlank { ANDROID_UNKNOWN_SSID } ?: ANDROID_UNKNOWN_SSID

        val bssid =
            bssidMatch
                ?.groupValues
                ?.get(1)
                ?.trim { it.isWhitespace() || it == '\\' }
                ?.ifBlank { ANDROID_UNKNOWN_BSSID } ?: ANDROID_UNKNOWN_BSSID

        return WifiDetails(ssid, bssid.uppercase())
    }

    private val privateDnsFlow: Flow<PrivateDnsSettings> = callbackFlow {
        val contentResolver = appContext.contentResolver

        val modeUri = Settings.Global.getUriFor("private_dns_mode")
        val specifierUri = Settings.Global.getUriFor("private_dns_specifier")

        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    trySend(getCurrentPrivateDnsSettings())
                }
            }

        contentResolver.registerContentObserver(modeUri, false, observer)
        contentResolver.registerContentObserver(specifierUri, false, observer)

        // initial value
        trySend(getCurrentPrivateDnsSettings())

        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }

    private data class PrivateDnsSettings(val mode: PrivateDnsMode, val hostname: String?)

    private fun getCurrentPrivateDnsSettings(): PrivateDnsSettings {
        val modeStr =
            Settings.Global.getString(appContext.contentResolver, "private_dns_mode") ?: "off"

        val mode =
            when (modeStr) {
                "hostname" -> PrivateDnsMode.HOSTNAME
                "opportunistic" -> PrivateDnsMode.AUTOMATIC
                else -> PrivateDnsMode.OFF
            }

        val hostname =
            Settings.Global.getString(appContext.contentResolver, "private_dns_specifier")

        return PrivateDnsSettings(mode, hostname)
    }

    private fun getDnsServers(network: Network?): List<String> {
        if (network == null) return emptyList()
        val linkProperties = connectivityManager?.getLinkProperties(network) ?: return emptyList()
        return linkProperties.dnsServers.map { it.hostAddress }
    }

    fun hasIpv6Support(network: Network?, activeNetwork: ActiveNetwork): Boolean {
        if (network == null || activeNetwork is ActiveNetwork.Disconnected) return false

        val lp = connectivityManager?.getLinkProperties(network) ?: return false

        val hasGlobalIpv6 =
            lp.linkAddresses.any {
                val addr = it.address
                addr is Inet6Address &&
                    !addr.isLinkLocalAddress &&
                    !addr.isLoopbackAddress &&
                    !addr.isMulticastAddress
            }

        val hasIpv6DefaultRoute =
            lp.routes.any { route -> route.isDefaultRoute && (route.gateway is Inet6Address) }

        val hasNat64Prefix =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lp.nat64Prefix != null
            } else false

        val hasSupport = (hasGlobalIpv6 && hasIpv6DefaultRoute) || hasNat64Prefix

        return hasSupport
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val defaultNetworkFlow: Flow<TransportEvent> =
        combine(configurationListener.detectionMethod, permissionsChangedFlow) { detectionMethod, _
                ->
                detectionMethod
            }
            .flatMapLatest { detectionMethod ->
                callbackFlow {
                    val defaultNetworkCallback =
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                detectionMethod == DEFAULT
                        ) {
                            object :
                                ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                                override fun onAvailable(network: Network) {
                                    Timber.d("Default onAvailable: $network")
                                }

                                override fun onLost(network: Network) {
                                    trySend(TransportEvent.Lost(network))
                                }

                                override fun onCapabilitiesChanged(
                                    network: Network,
                                    caps: NetworkCapabilities,
                                ) {
                                    trySend(TransportEvent.CapabilitiesChanged(network, caps))
                                }
                            }
                        } else {
                            object : ConnectivityManager.NetworkCallback() {
                                override fun onAvailable(network: Network) {
                                    Timber.d("Default onAvailable: $network")
                                }

                                override fun onLost(network: Network) {
                                    trySend(TransportEvent.Lost(network))
                                }

                                override fun onCapabilitiesChanged(
                                    network: Network,
                                    caps: NetworkCapabilities,
                                ) {
                                    trySend(TransportEvent.CapabilitiesChanged(network, caps))
                                }
                            }
                        }
                    connectivityManager?.registerDefaultNetworkCallback(defaultNetworkCallback)

                    trySend(
                        TransportEvent.Permissions(
                            Permissions(
                                locationManager?.isLocationServicesEnabled() ?: false,
                                appContext.hasRequiredLocationPermissions(),
                            )
                        )
                    )

                    awaitClose {
                        connectivityManager?.unregisterNetworkCallback(defaultNetworkCallback)
                    }
                }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val wifiFlow: Flow<TransportEvent> =
        combine(configurationListener.detectionMethod, permissionsChangedFlow) { detectionMethod, _
                ->
                detectionMethod
            }
            .flatMapLatest { detectionMethod -> createWifiNetworkCallbackFlow(detectionMethod) }

    private fun createWifiNetworkCallbackFlow(
        detectionMethod: WifiDetectionMethod
    ): Flow<TransportEvent> =
        callbackFlow {
                val onAvailable: (Network) -> Unit = { network ->
                    // ignore onAvailable has it doesn't contain detailed network information in
                    // capabilities
                    Timber.d("WiFi onAvailable: $network")
                }
                val onLost: (Network) -> Unit = { network ->
                    Timber.d("WiFi onLost: $network")
                    trySend(TransportEvent.Lost(network))
                }
                val onCapabilitiesChanged: (Network, NetworkCapabilities) -> Unit =
                    { network, caps ->
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            trySend(TransportEvent.CapabilitiesChanged(network, caps))
                        }
                    }

                val onLinkPropertiesChanged: (Network, LinkProperties) -> Unit =
                    { network, linkProps ->
                        trySend(TransportEvent.LinkPropertiesChanged(network, linkProps))
                    }

                val wifiCallback =
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && detectionMethod == DEFAULT
                    ) {
                        object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                            override fun onAvailable(network: Network) = onAvailable(network)

                            override fun onLost(network: Network) = onLost(network)

                            override fun onCapabilitiesChanged(
                                network: Network,
                                caps: NetworkCapabilities,
                            ) = onCapabilitiesChanged(network, caps)

                            override fun onLinkPropertiesChanged(
                                network: Network,
                                linkProperties: LinkProperties,
                            ) = onLinkPropertiesChanged(network, linkProperties)
                        }
                    } else {
                        object : ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: Network) = onAvailable(network)

                            override fun onLost(network: Network) = onLost(network)

                            override fun onCapabilitiesChanged(
                                network: Network,
                                caps: NetworkCapabilities,
                            ) = onCapabilitiesChanged(network, caps)

                            override fun onLinkPropertiesChanged(
                                network: Network,
                                linkProperties: LinkProperties,
                            ) = onLinkPropertiesChanged(network, linkProperties)
                        }
                    }

                val request =
                    NetworkRequest.Builder()
                        .apply { addTransportType(NetworkCapabilities.TRANSPORT_WIFI) }
                        .build()

                connectivityManager?.registerNetworkCallback(request, wifiCallback)

                awaitClose {
                    runCatching { connectivityManager?.unregisterNetworkCallback(wifiCallback) }
                        .onFailure { Timber.e(it, "Error unregistering WiFi network callback") }
                }
            }
            .onStart { emit(TransportEvent.Unknown) }

    private val cellularFlow: Flow<TransportEvent> =
        callbackFlow {
                val onAvailable: (Network) -> Unit = { network ->
                    Timber.d("Cellular onAvailable: $network")
                    val caps = connectivityManager?.getNetworkCapabilities(network)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        activeCellularNetworks.update { it + (network to caps) }
                        trySend(TransportEvent.CapabilitiesChanged(network, caps))
                    }
                }

                val onLost: (Network) -> Unit = { network ->
                    Timber.d("Cellular onLost: $network")
                    activeCellularNetworks.update { it - network }
                    trySend(TransportEvent.Lost(network))
                }

                val onCapabilitiesChanged: (Network, NetworkCapabilities) -> Unit =
                    { network, caps ->
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            activeCellularNetworks.update { it + (network to caps) }
                            trySend(TransportEvent.CapabilitiesChanged(network, caps))
                        }
                    }

                val cellularCallback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) = onAvailable(network)

                        override fun onLost(network: Network) = onLost(network)

                        override fun onCapabilitiesChanged(
                            network: Network,
                            caps: NetworkCapabilities,
                        ) = onCapabilitiesChanged(network, caps)
                    }

                val request =
                    NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .build()

                connectivityManager?.registerNetworkCallback(request, cellularCallback)

                awaitClose {
                    runCatching { connectivityManager?.unregisterNetworkCallback(cellularCallback) }
                        .onFailure { Timber.e(it, "Error unregistering cellular network callback") }
                }
            }
            .onStart { emit(TransportEvent.Unknown) }

    private val ethernetFlow: Flow<TransportEvent> =
        callbackFlow {
                val onAvailable: (Network) -> Unit = { network ->
                    Timber.d("Ethernet onAvailable: $network")
                }
                val onLost: (Network) -> Unit = { network ->
                    Timber.d("Ethernet onLost: $network")
                    trySend(TransportEvent.Lost(network))
                }
                val onCapabilitiesChanged: (Network, NetworkCapabilities) -> Unit =
                    { network, caps ->
                        Timber.d("Ethernet onCapabilitiesChanged: $network")
                        trySend(TransportEvent.CapabilitiesChanged(network, caps))
                    }

                val ethernetCallback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) = onAvailable(network)

                        override fun onLost(network: Network) = onLost(network)

                        override fun onCapabilitiesChanged(
                            network: Network,
                            caps: NetworkCapabilities,
                        ) = onCapabilitiesChanged(network, caps)
                    }

                val request =
                    NetworkRequest.Builder()
                        .apply { addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET) }
                        .build()

                connectivityManager?.registerNetworkCallback(request, ethernetCallback)

                awaitClose {
                    runCatching { connectivityManager?.unregisterNetworkCallback(ethernetCallback) }
                        .onFailure { Timber.e(it, "Error unregistering ethernet network callback") }
                }
            }
            .onStart { emit(TransportEvent.Unknown) }

    private suspend fun getWifiDetailsByDetectionMethod(
        detectionMethod: WifiDetectionMethod?,
        networkCapabilities: NetworkCapabilities?,
        network: Network?,
    ): WifiDetails {
        val method = detectionMethod ?: DEFAULT
        return try {
                when (method) {
                    DEFAULT -> {
                        val (ssid, bssid) =
                            networkCapabilities?.getWifiSsidAndBssid()
                                ?: wifiManager?.getWifiSsidAndBssid()
                                ?: (ANDROID_UNKNOWN_SSID to ANDROID_UNKNOWN_BSSID)
                        WifiDetails(ssid, bssid.uppercase())
                    }
                    LEGACY -> {
                        val lastActive = lastKnownActiveNetwork.value
                        if (
                            lastActive is ActiveNetwork.Wifi &&
                                lastActive.networkId == network?.toString() &&
                                lastActive.ssid != ANDROID_UNKNOWN_SSID &&
                                lastActive.bssid != ANDROID_UNKNOWN_BSSID
                        ) {
                            Timber.d("Using last active network SSID+BSSID (LEGACY cache)")
                            return WifiDetails(lastActive.ssid, lastActive.bssid.uppercase())
                        }
                        Timber.d("Triggering new location ping for SSID and BSSID (LEGACY)")
                        val (ssid, bssid) =
                            wifiManager?.getWifiSsidAndBssid()
                                ?: (ANDROID_UNKNOWN_SSID to ANDROID_UNKNOWN_BSSID)
                        WifiDetails(ssid, bssid)
                    }
                    ROOT,
                    SHIZUKU -> {
                        val raw =
                            withTimeoutOrNull(SHELL_COMMAND_TIMEOUT_MS.milliseconds) {
                                if (method == ROOT) {
                                    configurationListener.runRootShellCommand(
                                        WIFI_INFO_SHELL_COMMAND
                                    )
                                } else {
                                    ShizukuShell(applicationScope)
                                        .singleResponseCommand(WIFI_INFO_SHELL_COMMAND)
                                }
                            } ?: ""
                        parseWifiStatusOutput(raw)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get WiFi details with method: ${method.name}")
                WifiDetails(ANDROID_UNKNOWN_SSID, ANDROID_UNKNOWN_BSSID)
            }
            .also {
                Timber.d(
                    "Current WiFi details via ${method.name}: ssid=${it.ssid}, bssid=${it.bssid}"
                )
            }
    }

    private fun hasValidatedInternet(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            hasNotSuspended(caps)
    }

    // default network events don't contain detailed capability information of underlying networks,
    // so we need to track separately
    private data class NetworkData(
        val defaultNetworkEvent: TransportEvent,
        val wifiNetworkEvent: TransportEvent,
        val cellularEvent: TransportEvent,
        val ethernetEvent: TransportEvent,
    )

    // combine our network flows to keep sync
    private val networkFlows: Flow<NetworkData> =
        combine(defaultNetworkFlow, wifiFlow, cellularFlow, ethernetFlow) {
            defaultEvent,
            wifiEvent,
            cellularEvent,
            ethernetEvent ->
            NetworkData(defaultEvent, wifiEvent, cellularEvent, ethernetEvent)
        }

    private fun hasNotSuspended(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
    }

    // For multi-sim selection, prefers foreground, then validated internet, then not suspended
    private fun pickBestCellularNetworkEntry(): Map.Entry<Network, NetworkCapabilities>? {
        val networksMap = activeCellularNetworks.value
        if (networksMap.isEmpty()) return null

        return networksMap.entries.maxByOrNull { (_, caps) ->
            // Network is an internal carrier MMS network
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)) {
                return@maxByOrNull -1000
            }

            var score = 0

            // OS prefers this network if Foreground
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)) score += 100

            if (hasValidatedInternet(caps)) score += 50

            if (hasNotSuspended(caps)) score += 20

            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) score += 10

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
            ) {
                score += 5
            }

            // Prefer higher bandwidth network as tiebreaker
            val bandwidthPoints = (caps.linkDownstreamBandwidthKbps / 100_000).coerceIn(0, 4)
            score += bandwidthPoints

            score
        }
    }

    @OptIn(FlowPreview::class)
    override val connectivityStateFlow: SharedFlow<ConnectivityState> =
        combine(
                networkFlows,
                airplaneModeState,
                configurationListener.detectionMethod,
                privateDnsFlow,
            ) { networkData, isAirplaneOn, detectionMethod, privateDnsSettings ->
                val defaultEvent = networkData.defaultNetworkEvent
                val permissions =
                    when (defaultEvent) {
                        is TransportEvent.Permissions -> defaultEvent.permissions
                        else ->
                            Permissions(
                                locationManager?.isLocationServicesEnabled() ?: false,
                                appContext.hasRequiredLocationPermissions(),
                            )
                    }

                // determine default network capabilities and network
                val (defaultCaps, defaultNetwork) =
                    when (defaultEvent) {
                        is TransportEvent.CapabilitiesChanged ->
                            defaultEvent.networkCapabilities to defaultEvent.network
                        else ->
                            connectivityManager?.activeNetwork?.let { network ->
                                connectivityManager.getNetworkCapabilities(network) to network
                            } ?: (null to null)
                    }

                if (defaultCaps == null || defaultNetwork == null) {
                    return@combine ConnectivityState(
                        activeNetwork = ActiveNetwork.Disconnected(),
                        cellularNetworks = emptyMap(),
                        locationPermissionsGranted = permissions.locationPermissionGranted,
                        locationServicesEnabled = permissions.locationServicesEnabled,
                        airplaneModeOn = isAirplaneOn,
                        vpnState = VpnState.Inactive,
                    )
                }

                val isVpnActive = defaultCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

                val vpnState: VpnState =
                    if (!isVpnActive) {
                        VpnState.Inactive
                    } else {
                        VpnState.Active(
                            hasInternet =
                                defaultCaps.hasCapability(
                                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                                )
                        )
                    }

                val physicalNetwork: ActiveNetwork =
                    when {
                        networkData.ethernetEvent is TransportEvent.CapabilitiesChanged &&
                            networkData.ethernetEvent.networkCapabilities?.hasTransport(
                                NetworkCapabilities.TRANSPORT_ETHERNET
                            ) == true -> {
                            ActiveNetwork.Ethernet(
                                networkData.ethernetEvent.network,
                                networkData.ethernetEvent.networkCapabilities,
                            )
                        }

                        networkData.wifiNetworkEvent is TransportEvent.CapabilitiesChanged &&
                            networkData.wifiNetworkEvent.networkCapabilities?.hasTransport(
                                NetworkCapabilities.TRANSPORT_WIFI
                            ) == true -> {
                            val wifiEvent = networkData.wifiNetworkEvent
                            buildWifiNetwork(
                                network = wifiEvent.network,
                                caps = wifiEvent.networkCapabilities,
                                detectionMethod = detectionMethod,
                                lastActive = lastKnownActiveNetwork.value,
                            )
                        }

                        // Fallback for WiFi
                        defaultCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                            defaultNetwork != null -> {
                            buildWifiNetwork(
                                network = defaultNetwork,
                                caps = defaultCaps,
                                detectionMethod = detectionMethod,
                                lastActive = lastKnownActiveNetwork.value,
                            )
                        }

                        // Fallback for Ethernet
                        defaultCaps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true &&
                            defaultNetwork != null -> {
                            ActiveNetwork.Ethernet(defaultNetwork, defaultCaps)
                        }
                        else -> {
                            val bestCellularEntry =
                                pickBestCellularNetworkEntry()
                                    ?: activeCellularNetworks.value.entries.firstOrNull()

                            if (bestCellularEntry != null) {
                                ActiveNetwork.Cellular(
                                    bestCellularEntry.key,
                                    bestCellularEntry.value,
                                )
                            } else {
                                ActiveNetwork.Disconnected()
                            }
                        }
                    }

                lastKnownActiveNetwork.value = physicalNetwork

                val underlyingNetwork: Network? =
                    when (physicalNetwork) {
                        is ActiveNetwork.Wifi -> physicalNetwork.network
                        is ActiveNetwork.Cellular -> physicalNetwork.network
                        is ActiveNetwork.Ethernet -> physicalNetwork.network
                        else -> null
                    }

                val effectiveDns =
                    DnsInfo(
                        servers = getDnsServers(defaultNetwork),
                        privateDnsMode = privateDnsSettings.mode,
                        privateDnsHostname = privateDnsSettings.hostname,
                    )
                val underlyingDns =
                    DnsInfo(
                        servers = getDnsServers(underlyingNetwork),
                        privateDnsMode = privateDnsSettings.mode,
                        privateDnsHostname = privateDnsSettings.hostname,
                    )

                ConnectivityState(
                    activeNetwork = physicalNetwork,
                    cellularNetworks = activeCellularNetworks.value,
                    locationPermissionsGranted = permissions.locationPermissionGranted,
                    locationServicesEnabled = permissions.locationServicesEnabled,
                    vpnState = vpnState,
                    airplaneModeOn = isAirplaneOn,
                    effectiveDnsInfo = effectiveDns,
                    underlyingDnsInfo = underlyingDns,
                    hasIpv6 = hasIpv6Support(underlyingNetwork, physicalNetwork),
                )
            }
            .distinctUntilChanged()
            .debounce(300.milliseconds)
            .shareIn(applicationScope, SharingStarted.Eagerly, replay = 1)

    private suspend fun buildWifiNetwork(
        network: Network,
        caps: NetworkCapabilities,
        detectionMethod: WifiDetectionMethod,
        lastActive: ActiveNetwork?,
    ): ActiveNetwork.Wifi {
        val currentNetworkId = network.toString()

        // Use cache in legacy mode
        val (ssid, securityType, bssid) =
            if (
                detectionMethod == LEGACY &&
                    lastActive is ActiveNetwork.Wifi &&
                    lastActive.networkId == currentNetworkId &&
                    lastActive.ssid != ANDROID_UNKNOWN_SSID &&
                    lastActive.bssid != ANDROID_UNKNOWN_BSSID
            ) {
                Triple(lastActive.ssid, lastActive.securityType, lastActive.bssid)
            } else {
                // Fallback
                val wifiDetails = getWifiDetailsByDetectionMethod(detectionMethod, caps, network)
                val fetchedSecurity =
                    if (
                        detectionMethod == DEFAULT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ) {
                        caps.getWifiSecurityType()
                    } else {
                        wifiManager?.getLegacySecurityType()
                    }
                Triple(wifiDetails.ssid, fetchedSecurity, wifiDetails.bssid)
            }

        val linkPropsSnapshot =
            LinkPropertiesSnapshot.from(connectivityManager?.getLinkProperties(network))

        return ActiveNetwork.Wifi(
            ssid = ssid,
            bssid = bssid,
            securityType = securityType,
            networkId = currentNetworkId,
            network = network,
            capabilities = caps,
            linkProperties = linkPropsSnapshot,
        )
    }

    // utility to send local broadcast to trigger a recheck of location permissions onResume,
    // especially for getting SSID
    // that we did not have permission to read before, will trigger a recreation of the Wi-Fi flows
    // if permission was changed
    override fun checkPermissionsAndUpdateState() {
        val action = actionPermissionCheck
        val intent = Intent(action).apply { setPackage(appContext.packageName) }
        Timber.d("Sending broadcast: $action")
        appContext.sendBroadcast(intent)
    }
}
