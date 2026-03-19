package com.zaneschepke.wireguardautotunnel.ui.navigation

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.Settings
import androidx.navigation3.runtime.NavKey
import com.zaneschepke.wireguardautotunnel.R
import kotlinx.serialization.Serializable

@Keep
@Serializable
sealed class Route : NavKey {

    @Keep @Serializable data object Support : Route()

    @Keep @Serializable data object Lock : Route()

    @Keep @Serializable data object License : Route()

    @Keep @Serializable data object Logs : Route()

    @Keep @Serializable data object Appearance : Route()

    @Keep @Serializable data object Language : Route()

    @Keep @Serializable data object Display : Route()

    @Keep @Serializable data object Tunnels : Route()

    @Keep @Serializable data class TunnelSettings(val id: Int) : Route()

    @Keep @Serializable data class Config(val id: Int?) : Route()

    @Keep @Serializable data class SplitTunnel(val id: Int) : Route()

    @Keep @Serializable data class ConfigGlobal(val id: Int?) : Route()

    @Keep @Serializable data class SplitTunnelGlobal(val id: Int) : Route()

    @Keep @Serializable data object Sort : Route()

    @Keep @Serializable data object Settings : Route()

    @Keep @Serializable data object TunnelMonitoring : Route()

    @Keep @Serializable data object AndroidIntegrations : Route()

    @Keep @Serializable data object Dns : Route()

    @Keep @Serializable data object ProxySettings : Route()

    @Keep @Serializable data object LockdownSettings : Route()

    @Keep @Serializable data object AutoTunnel : Route()

    @Keep @Serializable data object AdvancedAutoTunnel : Route()

    @Keep @Serializable data object WifiDetectionMethod : Route()

    @Keep @Serializable data object WifiPreferences : Route()

    @Keep @Serializable data object LocationDisclosure : Route()

    @Keep @Serializable data object Donate : Route()

    @Keep @Serializable data object Addresses : Route()

    @Keep @Serializable data class PreferredTunnel(val tunnelNetwork: TunnelNetwork) : Route()

    @Keep @Serializable data object PingTarget : Route()

    @Keep @Serializable data object AutoRestart : Route()

    @Keep @Serializable data object FallbackTunnel : Route()
}

@Serializable
enum class TunnelNetwork {
    MOBILE_DATA,
    ETHERNET,
    WIFI,
}

enum class Tab(
    val startRoute: Route,
    val titleRes: Int,
    val inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val index: Int,
) {
    TUNNELS(Route.Tunnels, R.string.tunnels, Icons.Outlined.Home, Icons.Filled.Home, 0),
    AUTOTUNNEL(Route.AutoTunnel, R.string.auto_tunnel, Icons.Outlined.Bolt, Icons.Filled.Bolt, 1),
    SETTINGS(Route.Settings, R.string.settings, Icons.Outlined.Settings, Icons.Filled.Settings, 2),
    SUPPORT(
        Route.Support,
        R.string.support,
        Icons.Outlined.QuestionMark,
        Icons.Filled.QuestionMark,
        3,
    );

    companion object {
        fun fromRoute(route: Route): Tab =
            when (route) {
                is Route.Tunnels,
                Route.Sort,
                is Route.TunnelSettings,
                is Route.Config,
                is Route.Lock,
                is Route.SplitTunnel -> TUNNELS
                is Route.AutoTunnel,
                Route.AdvancedAutoTunnel,
                Route.WifiDetectionMethod,
                Route.WifiPreferences,
                is Route.PreferredTunnel,
                Route.LocationDisclosure -> AUTOTUNNEL
                is Route.Settings,
                Route.TunnelMonitoring,
                Route.AndroidIntegrations,
                Route.Dns,
                is Route.SplitTunnelGlobal,
                Route.ProxySettings,
                Route.LockdownSettings,
                Route.Appearance,
                Route.Language,
                Route.Display,
                Route.PingTarget,
                Route.AutoRestart,
                Route.FallbackTunnel,
                is Route.ConfigGlobal,
                Route.Logs -> SETTINGS
                is Route.Support,
                Route.License,
                Route.Donate,
                Route.Addresses -> SUPPORT
            }
    }
}
