package com.zaneschepke.wireguardautotunnel.ui.screens.settings.advanced

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.HdrAuto
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.SwitchWithDivider
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.rememberRestartToApplyChanges
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.common.text.ScreenSection
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
fun AdvancedScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val navController = LocalNavController.current
    val sharedUiState by sharedViewModel.collectAsState()
    val uiState by viewModel.collectAsState()
    if (uiState.isLoading) return

    val strictSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val strictApplies = sharedUiState.tunnelMode == TunnelMode.VPN
    val proxyDisabled = sharedUiState.tunnelMode == TunnelMode.PROXY
    val vpnActive = uiState.tunnelActive && strictApplies
    var pendingStrict by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var pendingGlobalSplit by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val applyStrict =
        rememberRestartToApplyChanges(needsRestart = vpnActive) { restart ->
            pendingStrict?.let { viewModel.setStrictSplitTunnel(it, restart) }
            pendingStrict = null
        }
    val applyGlobalSplit =
        rememberRestartToApplyChanges(needsRestart = vpnActive) { restart ->
            pendingGlobalSplit?.let { viewModel.setGlobalSplitTunneling(it, restart) }
            pendingGlobalSplit = null
        }

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
        Column {
            GroupLabel(
                stringResource(AdvancedSection.PRIVACY.title),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (strictSupported) {
                SurfaceRow(
                    leading = {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint =
                                if (strictApplies) MaterialTheme.colorScheme.onSurface
                                else Disabled,
                        )
                    },
                    enabled = strictApplies,
                    title = stringResource(R.string.strict_split_tunnel),
                    trailing = { modifier ->
                        ThemedSwitch(
                            checked = uiState.settings.strictSplitTunnelEnabled,
                            onClick = {
                                pendingStrict = it
                                applyStrict()
                            },
                            enabled = strictApplies,
                            modifier = modifier,
                        )
                    },
                    description = {
                        DescriptionText(
                            stringResource(
                                if (strictApplies) R.string.strict_split_tunnel_desc
                                else R.string.unavailable_in_mode,
                            ),
                            disabled = !strictApplies,
                        )
                    },
                    onClick = {
                        pendingStrict = !uiState.settings.strictSplitTunnelEnabled
                        applyStrict()
                    },
                )
            }
            SurfaceRow(
                leading = { Icon(Icons.Outlined.HdrAuto, contentDescription = null) },
                title = stringResource(R.string.amnezia_for_all_tunnels),
                trailing = { modifier ->
                    SwitchWithDivider(
                        checked = uiState.settings.isGlobalAmneziaEnabled,
                        onClick = { viewModel.setGlobalAmneziaEnabled(it) },
                        modifier = modifier,
                    )
                },
                description = {
                    DescriptionText(stringResource(R.string.amnezia_for_all_tunnels_desc))
                },
                onClick = { navController.push(Route.GlobalAmnezia) },
            )
        }
        Column {
            GroupLabel(
                stringResource(AdvancedSection.SPLIT_TUNNELING.title),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = {
                    Icon(
                        Icons.AutoMirrored.Outlined.CallSplit,
                        contentDescription = null,
                        tint = if (proxyDisabled) Disabled else MaterialTheme.colorScheme.onSurface,
                    )
                },
                enabled = !proxyDisabled,
                title = stringResource(R.string.split_tunneling_for_all_tunnels),
                trailing = { modifier ->
                    SwitchWithDivider(
                        checked = uiState.settings.isGlobalSplitTunnelEnabled,
                        onClick = {
                            pendingGlobalSplit = it
                            applyGlobalSplit()
                        },
                        modifier = modifier,
                        enabled = !proxyDisabled,
                    )
                },
                description = {
                    if (proxyDisabled) {
                        DescriptionText(
                            stringResource(R.string.unavailable_in_mode),
                            disabled = true,
                        )
                    } else {
                        DescriptionText(
                            stringResource(R.string.split_tunneling_for_all_tunnels_desc),
                        )
                        if (uiState.settings.isGlobalSplitTunnelEnabled) {
                            globalSummary?.let { DescriptionText(splitAppsLabel(it)) }
                        }
                    }
                },
                onClick = {
                    uiState.globalTunnelConfig?.let {
                        navController.push(Route.SplitTunnelGlobal(id = it.id))
                    }
                },
            )
        }
        Column {
            GroupLabel(
                stringResource(AdvancedSection.SCRIPTS.title),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                title = stringResource(R.string.tunnel_scripting),
                trailing = { modifier ->
                    ThemedSwitch(
                        checked = uiState.settings.tunnelScriptingEnabled,
                        onClick = { viewModel.setTunnelScriptedEnabled(it) },
                        modifier = modifier,
                    )
                },
                description = {
                    DescriptionText(stringResource(R.string.root_required_template, "").trim())
                },
                onClick = {
                    viewModel.setTunnelScriptedEnabled(!uiState.settings.tunnelScriptingEnabled)
                },
            )
        }
    }
}

/** Sections of the Advanced screen, in the order they appear. */
enum class AdvancedSection(@StringRes override val title: Int) : ScreenSection {
    PRIVACY(R.string.privacy_and_censorship),
    SPLIT_TUNNELING(R.string.split_tunneling),
    SCRIPTS(R.string.scripts),
}
