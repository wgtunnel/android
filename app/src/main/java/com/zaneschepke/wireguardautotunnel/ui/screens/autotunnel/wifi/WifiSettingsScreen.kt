package com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Filter1
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PublicOff
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.banner.WarningBanner
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberRotatingHint
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.navigation.TunnelNetwork
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.AutoTunnelScreenSideEffect
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.wifi.components.NetworkRuleInput
import com.zaneschepke.wireguardautotunnel.util.extensions.asTitleString
import com.zaneschepke.wireguardautotunnel.util.extensions.launchAppSettings
import com.zaneschepke.wireguardautotunnel.util.extensions.launchLocationServicesSettings
import com.zaneschepke.wireguardautotunnel.viewmodel.AutoTunnelViewModel
import org.koin.androidx.compose.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun WifiSettingsScreen(viewModel: AutoTunnelViewModel = koinViewModel()) {
    val context = LocalContext.current
    val navController = LocalNavController.current

    val uiState by viewModel.collectAsState()

    if (uiState.isLoading) return

    val wildcardEnabled = uiState.autoTunnelSettings.isWildcardsEnabled

    // Use hints from ViewModel
    val ssidHint = rememberRotatingHint(viewModel.ssidHints, wildcardEnabled)
    val bssidHint = rememberRotatingHint(viewModel.bssidHints, wildcardEnabled)

    var showLocationDialog by remember { mutableStateOf(false) }
    var currentSsidText by rememberSaveable { mutableStateOf("") }
    var currentBssidText by rememberSaveable { mutableStateOf("") }

    viewModel.collectSideEffect() {
        when (it) {
            AutoTunnelScreenSideEffect.BSSID_SAVED -> currentBssidText = ""
            AutoTunnelScreenSideEffect.SSID_SAVED -> currentSsidText = ""
        }
    }

    LaunchedEffect(uiState.autoTunnelSettings.trustedNetworkSSIDs) { currentSsidText = "" }
    LaunchedEffect(uiState.autoTunnelSettings.trustedNetworkBSSIDs) { currentBssidText = "" }

    val bssidFormatError = stringResource(R.string.invalid_bssid_format)

    val warnings by
        remember(
            uiState.connectivityState,
            uiState.autoTunnelSettings.trustedNetworkSSIDs,
            uiState.autoTunnelSettings.wifiDetectionMethod,
            uiState.tunnels,
        ) {
            derivedStateOf {
                val needsLocation =
                    uiState.autoTunnelSettings.wifiDetectionMethod.needsLocationPermissions()
                val hasConfigs =
                    uiState.autoTunnelSettings.trustedNetworkSSIDs.isNotEmpty() ||
                        uiState.tunnels.any { it.tunnelNetworks.isNotEmpty() }

                val showServicesWarning =
                    (uiState.connectivityState?.locationServicesEnabled == false) &&
                        needsLocation &&
                        hasConfigs
                val showPermissionsWarning =
                    (uiState.connectivityState?.locationPermissionsGranted == false) &&
                        needsLocation &&
                        hasConfigs

                showServicesWarning to showPermissionsWarning
            }
        }

    if (showLocationDialog) {
        InfoDialog(
            onAttest = {
                context.launchAppSettings()
                showLocationDialog = false
            },
            onDismiss = { showLocationDialog = false },
            title = stringResource(R.string.location_permissions),
            body = { Text(stringResource(R.string.location_justification)) },
            confirmText = stringResource(R.string.open_settings),
        )
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
    ) {
        Column {
            WarningBanner(
                stringResource(R.string.location_services_not_detected),
                warnings.first,
                trailing = {
                    TextButton({ context.launchLocationServicesSettings() }) {
                        Text(
                            stringResource(R.string.fix),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                onClick = { context.launchLocationServicesSettings() },
            )
            WarningBanner(
                stringResource(R.string.location_permissions_missing),
                warnings.second,
                trailing = {
                    TextButton({ showLocationDialog = true }) {
                        Text(
                            stringResource(R.string.fix),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                onClick = { showLocationDialog = true },
            )
        }
        Column {
            GroupLabel(stringResource(R.string.general), Modifier.padding(horizontal = 16.dp))
            SurfaceRow(
                leading = { Icon(Icons.Outlined.WifiFind, contentDescription = null) },
                title =
                    buildAnnotatedString {
                        append(stringResource(R.string.wifi_detection_method))
                        append(": ")
                        withStyle(
                            style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            append(
                                uiState.autoTunnelSettings.wifiDetectionMethod.asTitleString(
                                    context
                                )
                            )
                        }
                    },
                description = { DescriptionText(stringResource(R.string.wifi_detection_desc)) },
                onClick = { navController.push(Route.WifiDetectionMethod) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Filter1, contentDescription = null) },
                title = stringResource(R.string.use_wildcards),
                description = {
                    val descriptionText = stringResource(R.string.wildcard_desc)
                    val learnMoreText = stringResource(R.string.learn_more)
                    val url = stringResource(R.string.docs_wildcards)
                    val annotatedString = buildAnnotatedString {
                        append(descriptionText)
                        append(" ")

                        val start = length
                        append(learnMoreText)
                        val end = length

                        addLink(url = LinkAnnotation.Url(url), start = start, end = end)
                    }
                    DescriptionText(annotatedString)
                },
                trailing = {
                    ThemedSwitch(
                        checked = uiState.autoTunnelSettings.isWildcardsEnabled,
                        onClick = { viewModel.setWildcardsEnabled(it) },
                    )
                },
                onClick = {
                    viewModel.setWildcardsEnabled(!uiState.autoTunnelSettings.isWildcardsEnabled)
                },
            )
        }
        Column {
            GroupLabel(
                stringResource(R.string.wifi_rules),
                Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
            NetworkRuleInput(
                inputTitle = stringResource(R.string.trusted_wifi_names),
                placeholder = ssidHint,
                rules = uiState.autoTunnelSettings.trustedNetworkSSIDs,
                onDelete = { viewModel.removeTrustedNetworkName(it) },
                currentText = currentSsidText,
                onValueChange = { currentSsidText = it },
                onSave = { ssid -> viewModel.saveTrustedNetworkName(ssid) },
                supportingContent = {
                    if (uiState.autoTunnelSettings.isWildcardsEnabled)
                        DescriptionText(stringResource(R.string.wildcard_wifi_desc))
                },
            )

            NetworkRuleInput(
                inputTitle = stringResource(R.string.trusted_bssid),
                rules = uiState.autoTunnelSettings.trustedNetworkBSSIDs,
                onDelete = { viewModel.removeTrustedBssid(it) },
                currentText = currentBssidText,
                onValueChange = { currentBssidText = it },
                onSave = { bssid -> viewModel.saveTrustedBssid(bssid) },
                supportingContent = {
                    if (uiState.autoTunnelSettings.isWildcardsEnabled)
                        DescriptionText(stringResource(R.string.wildcard_bssid_desc))
                },
                placeholder = bssidHint,
            )

            SurfaceRow(
                leading = { Icon(Icons.Outlined.PublicOff, contentDescription = null) },
                title = stringResource(R.string.stop_while_captive_portal),
                onClick = {
                    viewModel.setDisabledOnCaptivePortal(
                        !uiState.autoTunnelSettings.disableTunnelOnCaptivePortal
                    )
                },
                trailing = {
                    ThemedSwitch(
                        checked = uiState.autoTunnelSettings.disableTunnelOnCaptivePortal,
                        onClick = { viewModel.setDisabledOnCaptivePortal(it) },
                    )
                },
                description = { DescriptionText(stringResource(R.string.captive_portal_desc)) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Map, contentDescription = null) },
                title = stringResource(R.string.tunnel_mapping),
                description = {
                    DescriptionText(stringResource(R.string.tunnel_mapping_description))
                },
                onClick = { navController.push(Route.PreferredTunnel(TunnelNetwork.WIFI)) },
            )
        }
    }
}
