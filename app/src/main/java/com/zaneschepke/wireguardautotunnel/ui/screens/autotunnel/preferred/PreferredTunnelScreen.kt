package com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.preferred

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.scrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberRotatingHint
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.TunnelNetwork
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.AutoTunnelScreenSideEffect
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.wifi.components.NetworkRuleInput
import com.zaneschepke.wireguardautotunnel.viewmodel.AutoTunnelViewModel
import org.koin.androidx.compose.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun PreferredTunnelScreen(
    tunnelNetwork: TunnelNetwork,
    viewModel: AutoTunnelViewModel = koinViewModel(),
) {
    val navController = LocalNavController.current

    val uiState by viewModel.collectAsState()

    var currentSsidText by rememberSaveable { mutableStateOf("") }
    var currentBssidText by rememberSaveable { mutableStateOf("") }

    viewModel.collectSideEffect() {
        when (it) {
            AutoTunnelScreenSideEffect.BSSID_SAVED -> currentBssidText = ""
            AutoTunnelScreenSideEffect.SSID_SAVED -> currentSsidText = ""
        }
    }

    if (uiState.isLoading) return

    var selectedTunnel by remember { mutableStateOf<TunnelConfig?>(null) }

    val wildcardEnabled = uiState.autoTunnelSettings.isWildcardsEnabled

    val ssidHint = rememberRotatingHint(viewModel.ssidHints, wildcardEnabled)
    val bssidHint = rememberRotatingHint(viewModel.bssidHints, wildcardEnabled)

    val currentSelection =
        remember(uiState.tunnels) {
            when (tunnelNetwork) {
                TunnelNetwork.MOBILE_DATA -> uiState.tunnels.firstOrNull { it.isMobileDataTunnel }

                TunnelNetwork.ETHERNET -> uiState.tunnels.firstOrNull { it.isEthernetTunnel }

                TunnelNetwork.WIFI -> null
            }
        }

    val lazyListState = rememberLazyListState()

    LazyColumn(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
        modifier =
            Modifier.pointerInput(Unit) { if (uiState.tunnels.isEmpty()) return@pointerInput }
                .overscroll(rememberOverscrollEffect())
                .scrollbar(lazyListState.scrollIndicatorState, Orientation.Vertical),
        state = lazyListState,
        userScrollEnabled = true,
        reverseLayout = false,
        flingBehavior = ScrollableDefaults.flingBehavior(),
    ) {
        item { GroupLabel(stringResource(R.string.tunnels), Modifier.padding(horizontal = 16.dp)) }
        if (tunnelNetwork != TunnelNetwork.WIFI) {
            item {
                SurfaceRow(
                    title =
                        buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(stringResource(R.string._default))
                            }
                        },
                    trailing =
                        if (currentSelection == null) {
                            {
                                Icon(
                                    Icons.Outlined.Check,
                                    stringResource(id = R.string.selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                    onClick = {
                        when (tunnelNetwork) {
                            TunnelNetwork.MOBILE_DATA -> {
                                viewModel.setPreferredMobileDataTunnel(null)
                                navController.pop()
                            }

                            TunnelNetwork.ETHERNET -> {
                                viewModel.setPreferredEthernetTunnel(null)
                                navController.pop()
                            }

                            TunnelNetwork.WIFI -> Unit
                        }
                    },
                )
            }
        }
        items(uiState.tunnels, key = { it.id }) { tunnel ->
            Column {
                SurfaceRow(
                    title = tunnel.name,
                    trailing =
                        if (currentSelection?.id == tunnel.id) {
                            {
                                Icon(
                                    Icons.Outlined.Check,
                                    stringResource(id = R.string.selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                    onClick = {
                        when (tunnelNetwork) {
                            TunnelNetwork.MOBILE_DATA -> {
                                viewModel.setPreferredMobileDataTunnel(tunnel)
                                navController.pop()
                            }

                            TunnelNetwork.ETHERNET -> {
                                viewModel.setPreferredEthernetTunnel(tunnel)
                                navController.pop()
                            }

                            TunnelNetwork.WIFI -> {
                                selectedTunnel = tunnel
                            }
                        }
                    },
                    description = {
                        val totalMapped = tunnel.tunnelNetworks.size + tunnel.tunnelBSSIDs.size

                        if (totalMapped > 0) {
                            DescriptionText(
                                stringResource(R.string.mapped_rules_count, totalMapped)
                            )
                        }
                    },
                )
                if (tunnel.id == selectedTunnel?.id) {
                    NetworkRuleInput(
                        inputTitle = stringResource(R.string.add_wifi_name),
                        placeholder = ssidHint,
                        rules = tunnel.tunnelNetworks,
                        onDelete = { viewModel.removeTunnelNetwork(tunnel, it) },
                        currentText = currentSsidText,
                        onValueChange = { currentSsidText = it },
                        onSave = { ssid -> viewModel.saveTunnelNetwork(tunnel, ssid) },
                        supportingContent = {
                            if (wildcardEnabled)
                                DescriptionText(stringResource(R.string.wildcard_wifi_desc))
                        },
                    )

                    NetworkRuleInput(
                        inputTitle = stringResource(R.string.add_bssid),
                        rules = tunnel.tunnelBSSIDs,
                        onDelete = { viewModel.removeTunnelBSSID(tunnel, it) },
                        currentText = currentBssidText,
                        onValueChange = { currentBssidText = it },
                        onSave = { bssid -> viewModel.saveTunnelBSSID(tunnel, bssid) },
                        supportingContent = {
                            if (uiState.autoTunnelSettings.isWildcardsEnabled)
                                DescriptionText(stringResource(R.string.wildcard_bssid_desc))
                        },
                        placeholder = bssidHint,
                    )
                }
            }
        }
    }
}
