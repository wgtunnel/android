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
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.components.QrCodeDialog
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.theme.Disabled
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.TunnelViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun TunnelSettingsScreen(
    viewModel: TunnelViewModel,
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val navController = LocalNavController.current

    val sharedUiState by sharedViewModel.container.stateFlow.collectAsStateWithLifecycle()

    val tunnelState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (tunnelState.isLoading) return
    val tunnel = tunnelState.tunnel ?: return

    var showQrModal by rememberSaveable { mutableStateOf(false) }

    sharedViewModel.collectSideEffect { sideEffect ->
        if (sideEffect is LocalSideEffect.Modal.QR) showQrModal = true
    }

    if (showQrModal) {
        QrCodeDialog(tunnelConfig = tunnel, onDismiss = { showQrModal = false })
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
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
            SurfaceRow(
                leading = {
                    Icon(
                        Icons.AutoMirrored.Outlined.CallSplit,
                        contentDescription = null,
                        tint =
                            if (sharedUiState.appMode == AppMode.PROXY) Disabled
                            else MaterialTheme.colorScheme.onSurface,
                    )
                },
                enabled = sharedUiState.appMode != AppMode.PROXY,
                title = stringResource(R.string.splt_tunneling),
                description =
                    if (sharedUiState.appMode == AppMode.PROXY) {
                        {
                            DescriptionText(
                                stringResource(R.string.unavailable_in_mode),
                                disabled = true,
                            )
                        }
                    } else null,
                onClick = { navController.push(Route.SplitTunnel(id = tunnel.id)) },
            )
        }
        Column {
            GroupLabel(
                stringResource(R.string.other),
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
                        checked = tunnel.restartOnPingFailure,
                        onClick = { viewModel.setRestartOnPing(it) },
                    )
                },
                onClick = { viewModel.setRestartOnPing(!tunnel.restartOnPingFailure) },
            )
            SurfaceRow(
                leading = {
                    Icon(ImageVector.vectorResource(R.drawable.host), contentDescription = null)
                },
                title = stringResource(R.string.prefer_ipv6_resolution),
                trailing = {
                    ThemedSwitch(
                        checked = !tunnel.isIpv4Preferred,
                        onClick = { viewModel.setIpv4Preferred(!it) },
                    )
                },
                onClick = { viewModel.setIpv4Preferred(!tunnel.isIpv4Preferred) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SurfaceRow(
                    leading = {
                        Icon(
                            Icons.Outlined.DataUsage,
                            contentDescription = null,
                            tint =
                                if (sharedUiState.appMode == AppMode.PROXY) Disabled
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    title = stringResource(R.string.metered_tunnel),
                    enabled = sharedUiState.appMode != AppMode.PROXY,
                    description =
                        if (sharedUiState.appMode == AppMode.PROXY) {
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
                            onClick = { viewModel.setMetered(it) },
                            enabled = sharedUiState.appMode != AppMode.PROXY,
                        )
                    },
                    onClick = { viewModel.setMetered(!tunnel.isMetered) },
                )
            }
        }
    }
}
