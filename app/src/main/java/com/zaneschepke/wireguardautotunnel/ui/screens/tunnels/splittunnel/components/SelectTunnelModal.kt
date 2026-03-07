package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog

@Composable
fun SelectTunnelModal(
    show: Boolean,
    tunnels: List<TunnelConfig>,
    onAttest: (tunnelConf: TunnelConfig?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTunnel by remember { mutableStateOf<TunnelConfig?>(null) }
    if (show) {
        InfoDialog(
            title = stringResource(R.string.copy_from),
            body = {
                LazyColumn(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Top,
                    modifier =
                        Modifier.pointerInput(Unit) { if (tunnels.isEmpty()) return@pointerInput }
                            .overscroll(rememberOverscrollEffect()),
                    state = rememberLazyListState(),
                    userScrollEnabled = true,
                    reverseLayout = false,
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                ) {
                    items(tunnels, key = { it.id }) { tunnel ->
                        SurfaceRow(
                            title = tunnel.name,
                            trailing =
                                if (selectedTunnel?.id == tunnel.id) {
                                    {
                                        Icon(
                                            Icons.Outlined.Check,
                                            stringResource(id = R.string.selected),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else null,
                            onClick = { selectedTunnel = tunnel },
                        )
                    }
                }
            },
            onAttest = { onAttest(selectedTunnel) },
            onDismiss = { onDismiss() },
            confirmText = stringResource(R.string.copy),
        )
    }
}
