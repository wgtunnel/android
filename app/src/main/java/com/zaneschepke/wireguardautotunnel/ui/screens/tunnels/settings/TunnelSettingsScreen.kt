package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.theme.Disabled
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.TunnelViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun TunnelSettingsScreen(
    viewModel: TunnelViewModel,
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val navController = LocalNavController.current

    val sharedUiState by sharedViewModel.collectAsState()

    val uiState by viewModel.collectAsState()

    if (uiState.isLoading) return
    val tunnel = uiState.tunnel ?: return

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column {
            GroupLabel(
                stringResource(R.string.configuration),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Description, contentDescription = null) },
                title = stringResource(R.string.view_configuration),
                onClick = { navController.push(Route.Config(tunnel.id)) },
            )
            if (uiState.activeConfig != null) {
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
                    title = stringResource(R.string.view_live_tunnel),
                    onClick = { navController.push(Route.Config(tunnel.id, true)) },
                )
            }
        }
        Column {
            GroupLabel(
                stringResource(R.string.general),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Star, contentDescription = null) },
                title = stringResource(R.string.primary_tunnel),
                description = {
                    Text(
                        text = stringResource(R.string.set_primary_tunnel),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                MaterialTheme.colorScheme.outline
                            ),
                    )
                },
                trailing = {
                    ThemedSwitch(
                        checked = tunnel.isPrimaryTunnel,
                        onClick = { viewModel.togglePrimaryTunnel() },
                    )
                },
                onClick = { viewModel.togglePrimaryTunnel() },
            )
        }
        Column {
            GroupLabel(
                stringResource(R.string.network),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = {
                    Icon(
                        Icons.AutoMirrored.Outlined.CallSplit,
                        contentDescription = null,
                        tint =
                            if (sharedUiState.tunnelMode == TunnelMode.PROXY) Disabled
                            else MaterialTheme.colorScheme.onSurface,
                    )
                },
                enabled = sharedUiState.tunnelMode != TunnelMode.PROXY,
                title = stringResource(R.string.splt_tunneling),
                description = {
                    if (sharedUiState.tunnelMode == TunnelMode.PROXY) {
                        DescriptionText(
                            stringResource(R.string.unavailable_in_mode),
                            disabled = true,
                        )
                    } else {
                        uiState.includedAppsCount?.let {
                            DescriptionText(stringResource(R.string.included_apps, it))
                        }
                        uiState.excludedAppsCount?.let {
                            DescriptionText(stringResource(R.string.excluded_apps, it))
                        }
                    }
                },
                onClick = { navController.push(Route.SplitTunnel(id = tunnel.id)) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Public, contentDescription = null) },
                title = stringResource(R.string.ipv6_settings),
                onClick = { navController.push(Route.IPv6(tunnel.id)) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SurfaceRow(
                    leading = {
                        Icon(
                            Icons.Outlined.DataUsage,
                            contentDescription = null,
                            tint =
                                if (sharedUiState.tunnelMode == TunnelMode.PROXY) Disabled
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    title = stringResource(R.string.metered_tunnel),
                    enabled = sharedUiState.tunnelMode != TunnelMode.PROXY,
                    description =
                        if (sharedUiState.tunnelMode == TunnelMode.PROXY) {
                            {
                                DescriptionText(
                                    stringResource(R.string.unavailable_in_mode),
                                    disabled = true,
                                )
                            }
                        } else null,
                    trailing = {
                        ThemedSwitch(
                            checked = tunnel.isMetered,
                            onClick = { viewModel.onMetered(it) },
                            enabled = sharedUiState.tunnelMode != TunnelMode.PROXY,
                        )
                    },
                    onClick = { viewModel.onMetered(!tunnel.isMetered) },
                )
            }
            Column {
                GroupLabel(
                    stringResource(R.string.automation),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                    title = stringResource(R.string.ddns_auto_update),
                    description = {
                        DescriptionText(stringResource(R.string.ddns_auto_update_description))
                    },
                    trailing = {
                        ThemedSwitch(
                            checked = tunnel.dynamicDnsEnabled,
                            onClick = { viewModel.onDynamicDns(it) },
                        )
                    },
                    onClick = { viewModel.onDynamicDns(!tunnel.dynamicDnsEnabled) },
                )
            }
        }
    }
}
