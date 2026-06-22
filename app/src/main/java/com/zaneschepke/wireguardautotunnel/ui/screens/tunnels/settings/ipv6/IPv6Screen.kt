package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.ipv6

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.zaneschepke.wireguardautotunnel.ui.theme.Disabled
import com.zaneschepke.wireguardautotunnel.viewmodel.TunnelViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun IPv6Screen(viewModel: TunnelViewModel) {

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
                stringResource(R.string.peer_endpoints),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SurfaceRow(
                leading = { Icon(Icons.Outlined.Public, contentDescription = null) },
                title = stringResource(R.string.prefer_ipv6),
                trailing = {
                    ThemedSwitch(
                        checked = tunnel.isIpv6Preferred,
                        onClick = { viewModel.onIPv6Action(IPv6Intent.ToggleIpv6Preferred(it)) },
                    )
                },
                description = { DescriptionText(stringResource(R.string.prefer_ipv6_desc)) },
                onClick = {
                    viewModel.onIPv6Action(IPv6Intent.ToggleIpv6Preferred(!tunnel.isIpv6Preferred))
                },
            )

            val iconTint =
                if (!tunnel.isIpv6Preferred) Disabled else MaterialTheme.colorScheme.onSurface
            val ipv6Enabled = tunnel.isIpv6Preferred

            SurfaceRow(
                leading = {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = iconTint)
                },
                title = stringResource(R.string.fallback_to_ipv4),
                onClick = {
                    viewModel.onIPv6Action(IPv6Intent.ToggleFallback(!tunnel.ipv4FallbackEnabled))
                },
                enabled = ipv6Enabled,
                description = {
                    DescriptionText(
                        stringResource(R.string.fallback_to_ipv4_desc),
                        disabled = !ipv6Enabled,
                    )
                },
                trailing = {
                    ThemedSwitch(
                        checked = tunnel.ipv4FallbackEnabled,
                        onClick = { viewModel.onIPv6Action(IPv6Intent.ToggleFallback(it)) },
                        enabled = ipv6Enabled,
                    )
                },
            )

            SurfaceRow(
                leading = {
                    Icon(Icons.Outlined.Restore, contentDescription = null, tint = iconTint)
                },
                title = stringResource(R.string.restore_ipv6),
                onClick = {
                    viewModel.onIPv6Action(IPv6Intent.ToggleRestore(!tunnel.ipv6RestoreEnabled))
                },
                enabled = ipv6Enabled,
                description = {
                    DescriptionText(
                        stringResource(R.string.restore_ipv6_desc),
                        disabled = !ipv6Enabled,
                    )
                },
                trailing = {
                    ThemedSwitch(
                        checked = tunnel.ipv6RestoreEnabled,
                        onClick = { viewModel.onIPv6Action(IPv6Intent.ToggleRestore(it)) },
                        enabled = ipv6Enabled,
                    )
                },
            )
        }
    }
}
