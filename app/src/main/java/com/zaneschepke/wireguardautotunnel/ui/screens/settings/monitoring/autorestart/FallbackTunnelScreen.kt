package com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring.autorestart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.viewmodel.MonitoringViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun FallbackTunnelScreen(viewModel: MonitoringViewModel = koinViewModel()) {
    val settingsState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (settingsState.isLoading) return

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DescriptionText(
            stringResource(R.string.enable_fallback_tunnel_description),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
        )
        GroupLabel(
            stringResource(R.string.per_tunnel_fallback),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        settingsState.tunnels.forEach { tunnel ->
            var expanded by remember(tunnel.id) { mutableStateOf(false) }
            val otherTunnels = settingsState.tunnels.filter { it.id != tunnel.id }
            val currentName =
                tunnel.fallbackTunnelId?.let { id -> otherTunnels.find { it.id == id }?.name }
                    ?: stringResource(R.string.use_default_fallback)

            SurfaceRow(
                title = tunnel.name,
                leading = { Icon(Icons.Outlined.AltRoute, contentDescription = null) },
                description = { DescriptionText(currentName) },
                onClick = { expanded = !expanded },
                expandedContent =
                    if (expanded)
                        ({
                            Column {
                                SurfaceRow(
                                    title = stringResource(R.string.use_default_fallback),
                                    selected = tunnel.fallbackTunnelId == null,
                                    onClick = {
                                        viewModel.setTunnelFallbackId(tunnel, null)
                                        expanded = false
                                    },
                                )
                                otherTunnels.forEach { other ->
                                    SurfaceRow(
                                        title = other.name,
                                        selected = tunnel.fallbackTunnelId == other.id,
                                        onClick = {
                                            viewModel.setTunnelFallbackId(tunnel, other.id)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        })
                    else null,
            )
        }
    }
}
