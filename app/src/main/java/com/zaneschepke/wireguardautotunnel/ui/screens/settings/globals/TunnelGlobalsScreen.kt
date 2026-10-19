package com.zaneschepke.wireguardautotunnel.ui.screens.settings.globals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.SwitchWithDivider
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components.splitAppsLabel
import com.zaneschepke.wireguardautotunnel.ui.state.splitAppListSummary
import com.zaneschepke.wireguardautotunnel.ui.theme.Disabled
import com.zaneschepke.wireguardautotunnel.viewmodel.SettingsViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun TunnelGlobalsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val navController = LocalNavController.current
    val sharedUiState by sharedViewModel.collectAsState()
    val uiState by viewModel.collectAsState()
    if (uiState.isLoading) return
    val globalQuickConfig = uiState.globalTunnelConfig?.quickConfig
    val globalSummary =
        remember(globalQuickConfig) {
            val iface = uiState.globalTunnelConfig?.getConfig()?.`interface`
            splitAppListSummary(
                includedCount = iface?.allIncludedApps?.size ?: 0,
                excludedCount = iface?.allExcludedApps?.size ?: 0,
                emptyMeansAllTunneled = true,
            )
        }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxSize(),
    ) {
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
            trailing = { modifier ->
                SwitchWithDivider(
                    checked = uiState.settings.isGlobalSplitTunnelEnabled,
                    onClick = { viewModel.setGlobalSplitTunneling(it) },
                    modifier = modifier,
                    enabled = sharedUiState.tunnelMode != TunnelMode.PROXY,
                )
            },
            description = {
                if (sharedUiState.tunnelMode == TunnelMode.PROXY) {
                    DescriptionText(stringResource(R.string.unavailable_in_mode), disabled = true)
                } else if (!uiState.settings.isGlobalSplitTunnelEnabled) {
                    DescriptionText(stringResource(R.string.global_split_off))
                } else {
                    globalSummary?.let { DescriptionText(splitAppsLabel(it)) }
                    DescriptionText(stringResource(R.string.global_split_applies))
                }
            },
            onClick = {
                uiState.globalTunnelConfig?.let {
                    navController.push(Route.SplitTunnelGlobal(id = it.id))
                }
            },
        )
        SurfaceRow(
            leading = { Icon(Icons.Outlined.Description, contentDescription = null) },
            title = stringResource(R.string.tunnel_configuration),
            onClick = {
                uiState.globalTunnelConfig?.let {
                    navController.push(Route.ConfigGlobal(id = it.id))
                }
            },
        )
    }
}
