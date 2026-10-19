package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.entry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components.TunnelPickerList
import com.zaneschepke.wireguardautotunnel.viewmodel.TunnelViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun EntryTunnelScreen(viewModel: TunnelViewModel) {
    val navController = LocalNavController.current
    val uiState by viewModel.collectAsState()

    if (uiState.isLoading) return
    val tunnel = uiState.tunnel ?: return

    val candidates =
        remember(uiState.userTunnels, tunnel.id) {
            uiState.userTunnels.filter { it.id != tunnel.id && it.entryTunnelId == null }
        }

    if (candidates.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            SurfaceRow(
                leading = {
                    Icon(Icons.AutoMirrored.Outlined.CallMerge, contentDescription = null)
                },
                title = stringResource(R.string.entry_tunnel_add_another),
                description = { DescriptionText(stringResource(R.string.entry_tunnel_desc)) },
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DescriptionText(
            stringResource(R.string.entry_tunnel_desc),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        TunnelPickerList(
            modifier = Modifier.weight(1f),
            tunnels = candidates,
            isSelected = { it.id == tunnel.entryTunnelId },
            onSelect = {
                viewModel.onEntryTunnel(it.id)
                navController.pop()
            },
            leading = { Icon(Icons.AutoMirrored.Outlined.CallMerge, contentDescription = null) },
            leadingItem = {
                val noneSelected = tunnel.entryTunnelId == null
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.LinkOff, contentDescription = null) },
                    title = stringResource(R.string.entry_tunnel_none),
                    description = {
                        DescriptionText(stringResource(R.string.entry_tunnel_none_desc))
                    },
                    selected = false,
                    trailing = {
                        if (noneSelected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        viewModel.onEntryTunnel(null)
                        navController.pop()
                    },
                )
            },
        )
    }
}
