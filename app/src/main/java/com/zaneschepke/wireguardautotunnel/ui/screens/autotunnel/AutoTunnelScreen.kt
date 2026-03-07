package com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel

import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PublicOff
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.SignalCellular4Bar
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.zaneschepke.networkmonitor.ActiveNetwork
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.SwitchWithDivider
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberClipboardHelper
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.navigation.TunnelNetwork
import com.zaneschepke.wireguardautotunnel.viewmodel.AutoTunnelViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AutoTunnelScreen(
    viewModel: AutoTunnelViewModel = koinViewModel(),
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val clipboard = rememberClipboardHelper()

    val globalUiState by sharedViewModel.container.stateFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (uiState.isLoading) return

    val batteryActivity =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            sharedViewModel.disableBatteryOptimizationsShown()
        }

    @SuppressLint("BatteryLife")
    fun requestDisableBatteryOptimizations() {
        batteryActivity.launch(
            Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = "package:${context.packageName}".toUri()
            }
        )
    }

    val (ethernetTunnel, mobileDataTunnel, mappedTunnels) =
        remember(uiState.tunnels) {
            Triple(
                uiState.tunnels.firstOrNull { it.isEthernetTunnel },
                uiState.tunnels.firstOrNull { it.isMobileDataTunnel },
                uiState.tunnels.any { it.tunnelNetworks.isNotEmpty() },
            )
        }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column {
            val (title, buttonText, icon) =
                remember(uiState.autoTunnelActive) {
                    when (uiState.autoTunnelActive) {
                        true ->
                            Triple(
                                context.getString(R.string.auto_tunnel_running),
                                context.getString(R.string.stop),
                                Icons.Outlined.CheckCircle,
                            )
                        false ->
                            Triple(
                                context.getString(R.string.auto_tunnel_not_running),
                                context.getString(R.string.start),
                                Icons.Outlined.Info,
                            )
                    }
                }

            fun onAutoTunnelClick() {
                if (!globalUiState.isBatteryOptimizationShown)
                    return requestDisableBatteryOptimizations()
                viewModel.toggleAutoTunnel(globalUiState.appMode)
            }

            SurfaceRow(
                leading = { Icon(icon, null) },
                title = title,
                trailing = {
                    Button({ onAutoTunnelClick() }) {
                        Text(
                            buttonText,
                            fontWeight = FontWeight.Bold,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.surface
                                ),
                        )
                    }
                },
                onClick = { onAutoTunnelClick() },
            )
        }
        Column {
            GroupLabel(
                stringResource(R.string.networks),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            val localizedNetworkType by
                remember(uiState.connectivityState) {
                    derivedStateOf {
                        when (uiState.connectivityState?.activeNetwork) {
                            is ActiveNetwork.Wifi -> context.getString(R.string.wifi)
                            is ActiveNetwork.Ethernet -> context.getString(R.string.ethernet)
                            is ActiveNetwork.Cellular -> context.getString(R.string.mobile_data)
                            is ActiveNetwork.Disconnected -> context.getString(R.string.no_network)
                            null -> context.getString(R.string.no_network)
                        }
                    }
                }

            SurfaceRow(
                leading = {
                    Icon(ImageVector.vectorResource(R.drawable.globe), contentDescription = null)
                },
                title =
                    buildAnnotatedString {
                        append(stringResource(R.string.active_network))
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(localizedNetworkType)
                        }
                    },
                description =
                    (uiState.connectivityState?.activeNetwork as? ActiveNetwork.Wifi)?.let {
                        {
                            Column {
                                DescriptionText(
                                    buildAnnotatedString {
                                        append(stringResource(R.string.security_type))
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(
                                                it.securityType?.name
                                                    ?: stringResource(R.string.unknown)
                                            )
                                        }
                                    }
                                )
                                DescriptionText(
                                    buildAnnotatedString {
                                        append(stringResource(R.string.network_name))
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(it.ssid)
                                        }
                                    }
                                )
                            }
                        }
                    },
                trailing =
                    if (uiState.connectivityState?.activeNetwork is ActiveNetwork.Wifi) {
                        { Icon(Icons.Outlined.ContentCopy, contentDescription = null) }
                    } else null,
                onClick = {
                    when (val network = uiState.connectivityState?.activeNetwork) {
                        is ActiveNetwork.Wifi ->
                            clipboard.copy(network.ssid, context.getString(R.string.wifi))
                        else -> Unit
                    }
                },
            )

            SurfaceRow(
                leading = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
                title = stringResource(R.string.tunnel_on_wifi),
                trailing = { modifier ->
                    SwitchWithDivider(
                        checked = uiState.autoTunnelSettings.isTunnelOnWifiEnabled,
                        onClick = { viewModel.setAutoTunnelOnWifiEnabled(it) },
                        modifier = modifier,
                    )
                },
                description = {
                    DescriptionText(
                        buildAnnotatedString {
                            append(stringResource(R.string.preferred_tunnel_label))
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(
                                    if (mappedTunnels) stringResource(R.string.mapped)
                                    else stringResource(R.string._default)
                                )
                            }
                        }
                    )
                },
                onClick = { navController.push(Route.WifiPreferences) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.SignalCellular4Bar, contentDescription = null) },
                title = stringResource(R.string.tunnel_mobile_data),
                trailing = { modifier ->
                    SwitchWithDivider(
                        checked = uiState.autoTunnelSettings.isTunnelOnMobileDataEnabled,
                        onClick = { viewModel.setTunnelOnCellular(it) },
                        modifier = modifier,
                    )
                },
                description = {
                    DescriptionText(
                        buildAnnotatedString {
                            append(stringResource(R.string.preferred_tunnel_label))
                            mobileDataTunnel?.name?.let { append(it) }
                                ?: withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(stringResource(R.string._default))
                                }
                        }
                    )
                },
                onClick = { navController.push(Route.PreferredTunnel(TunnelNetwork.MOBILE_DATA)) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.SettingsEthernet, contentDescription = null) },
                title = stringResource(R.string.tunnel_on_ethernet),
                trailing = { modifier ->
                    SwitchWithDivider(
                        checked = uiState.autoTunnelSettings.isTunnelOnEthernetEnabled,
                        onClick = { viewModel.setTunnelOnEthernet(it) },
                        modifier = modifier,
                    )
                },
                description = {
                    DescriptionText(
                        buildAnnotatedString {
                            append(stringResource(R.string.preferred_tunnel_label))
                            ethernetTunnel?.name?.let { append(it) }
                                ?: withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(stringResource(R.string._default))
                                }
                        }
                    )
                },
                onClick = { navController.push(Route.PreferredTunnel(TunnelNetwork.ETHERNET)) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.PublicOff, contentDescription = null) },
                title = stringResource(R.string.stop_on_no_internet),
                description = { DescriptionText(stringResource(R.string.stop_on_internet_loss)) },
                trailing = {
                    ThemedSwitch(
                        checked = uiState.autoTunnelSettings.isStopOnNoInternetEnabled,
                        onClick = { viewModel.setStopOnNoInternetEnabled(it) },
                    )
                },
                onClick = {
                    viewModel.setStopOnNoInternetEnabled(
                        !uiState.autoTunnelSettings.isStopOnNoInternetEnabled
                    )
                },
            )
        }
        Column {
            GroupLabel(
                stringResource(R.string.other),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                title = stringResource(R.string.restart_at_boot),
                trailing = {
                    ThemedSwitch(
                        checked = uiState.autoTunnelSettings.startOnBoot,
                        onClick = { viewModel.setStartAtBoot(it) },
                    )
                },
                onClick = { viewModel.setStartAtBoot(!uiState.autoTunnelSettings.startOnBoot) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                title = stringResource(R.string.advanced_settings),
                onClick = { navController.push(Route.AdvancedAutoTunnel) },
            )
        }
    }
}
