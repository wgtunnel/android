package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.wstunnel

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox
import com.zaneschepke.wireguardautotunnel.viewmodel.TunnelViewModel
import org.orbitmvi.orbit.compose.collectAsState

/**
 * wstunnel only publishes a prebuilt Android binary for arm64-v8a as of v10.6.1 - mirrors
 * WsTunnelService.isSupported() in the :wstunnel module, duplicated here rather than adding a
 * cross-module dependency just for this check.
 */
private fun isWsTunnelSupported(): Boolean = Build.SUPPORTED_ABIS.contains("arm64-v8a")

@Composable
fun WsTunnelScreen(viewModel: TunnelViewModel) {

    val uiState by viewModel.collectAsState()

    if (uiState.isLoading) return
    val tunnel = uiState.tunnel ?: return

    val supported = isWsTunnelSupported()
    val enabled = supported && tunnel.wsTunnelEnabled

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column {
            GroupLabel(
                stringResource(R.string.wstunnel_title),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SurfaceRow(
                leading = { Icon(Icons.Outlined.Shield, contentDescription = null) },
                title = stringResource(R.string.wstunnel_enable),
                enabled = supported,
                description = {
                    DescriptionText(
                        text =
                            if (supported) stringResource(R.string.wstunnel_enable_desc)
                            else stringResource(R.string.wstunnel_unsupported_device),
                        disabled = !supported,
                    )
                },
                trailing = {
                    ThemedSwitch(
                        checked = tunnel.wsTunnelEnabled,
                        enabled = supported,
                        onClick = {
                            viewModel.onWsTunnelAction(WsTunnelIntent.ToggleEnabled(it))
                        },
                    )
                },
                onClick = {
                    if (supported) {
                        viewModel.onWsTunnelAction(
                            WsTunnelIntent.ToggleEnabled(!tunnel.wsTunnelEnabled)
                        )
                    }
                },
            )
        }

        AnimatedVisibility(enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GroupLabel(
                    stringResource(R.string.wstunnel_server),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                ConfigurationTextBox(
                    modifier =
                        Modifier.padding(horizontal = 16.dp).padding(top = 8.dp).fillMaxWidth(),
                    leading = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
                    label = stringResource(R.string.wstunnel_server_url_label),
                    hint = stringResource(R.string.wstunnel_server_url_hint),
                    value = tunnel.wsTunnelServerUrl ?: "",
                    onValueChange = {
                        viewModel.onWsTunnelAction(WsTunnelIntent.UpdateServerUrl(it))
                    },
                )

                ConfigurationTextBox(
                    modifier =
                        Modifier.padding(horizontal = 16.dp).padding(top = 8.dp).fillMaxWidth(),
                    leading = { Icon(Icons.Outlined.Route, contentDescription = null) },
                    label = stringResource(R.string.wstunnel_path_prefix_label),
                    hint = stringResource(R.string.wstunnel_path_prefix_hint),
                    value = tunnel.wsTunnelPathPrefix ?: "",
                    onValueChange = {
                        viewModel.onWsTunnelAction(WsTunnelIntent.UpdatePathPrefix(it))
                    },
                )

                ConfigurationTextBox(
                    modifier =
                        Modifier.padding(horizontal = 16.dp).padding(top = 8.dp).fillMaxWidth(),
                    leading = { Icon(Icons.Outlined.Language, contentDescription = null) },
                    label = stringResource(R.string.wstunnel_sni_override_label),
                    hint = stringResource(R.string.wstunnel_sni_override_hint),
                    value = tunnel.wsTunnelSniOverride ?: "",
                    onValueChange = {
                        viewModel.onWsTunnelAction(WsTunnelIntent.UpdateSniOverride(it))
                    },
                )
            }
        }
    }
}
