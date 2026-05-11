package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.SwitchWithDivider
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelsUiState
import com.zaneschepke.wireguardautotunnel.util.extensions.asColor
import com.zaneschepke.wireguardautotunnel.util.extensions.openWebUrl
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TunnelList(
    uiState: TunnelsUiState,
    modifier: Modifier = Modifier,
    viewModel: SharedAppViewModel,
) {
    val navController = LocalNavController.current
    val context = LocalContext.current

    val lazyListState = rememberLazyListState()

    LazyColumn(
        horizontalAlignment = Alignment.Start,
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (uiState.tunnels.isEmpty()) return@detectTapGestures
                        viewModel.clearSelectedTunnels()
                    }
                }
                .overscroll(rememberOverscrollEffect()),
        state = lazyListState,
        userScrollEnabled = true,
        reverseLayout = false,
        flingBehavior = ScrollableDefaults.flingBehavior(),
    ) {
        if (uiState.tunnels.isEmpty()) {
            item {
                GettingStartedLabel(
                    onClick = { context.openWebUrl(it) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        items(uiState.tunnels, key = { it.id }) { tunnel ->
            val tunnelState =
                remember(uiState.backendStatus.activeTunnels) {
                    uiState.backendStatus.activeTunnels[tunnel.id] ?: ActiveTunnel()
                }
            val selected =
                remember(uiState.selectedTunnels) {
                    uiState.selectedTunnels.any { it.id == tunnel.id }
                }

            SurfaceRow(
                modifier = Modifier.animateItem(),
                leading = {
                    Icon(
                        Icons.Rounded.Circle,
                        contentDescription = stringResource(R.string.tunnel_monitoring),
                        tint = tunnelState.state.asColor(),
                        modifier = Modifier.size(14.dp),
                    )
                },
                title = tunnel.name,
                onClick = {
                    if (uiState.selectedTunnels.isNotEmpty()) {
                        viewModel.toggleSelectedTunnel(tunnel.id)
                    } else {
                        navController.push(Route.TunnelSettings(tunnel.id))
                        viewModel.clearSelectedTunnels()
                    }
                },
                selected = selected,
                expandedContent =
                    if (tunnelState.state !== Tunnel.State.Down) {
                        { TunnelStatisticsRow(tunnelState) }
                    } else null,
                onLongClick = { viewModel.toggleSelectedTunnel(tunnel.id) },
                trailing = { modifier ->
                    SwitchWithDivider(
                        checked = tunnelState.state !== Tunnel.State.Down,
                        onClick = { checked ->
                            if (checked) viewModel.startTunnel(tunnel)
                            else viewModel.stopTunnel(tunnel)
                        },
                        modifier = modifier,
                    )
                },
            )
        }
    }
}
