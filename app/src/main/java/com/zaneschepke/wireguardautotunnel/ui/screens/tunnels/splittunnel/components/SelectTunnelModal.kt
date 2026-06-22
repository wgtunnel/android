package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelSummary

@Composable
fun SelectTunnelModal(
    show: Boolean,
    tunnels: List<TunnelSummary>,
    selectedTunnelId: Int?,
    onSelect: (Int) -> Unit,
    onAttest: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    InfoDialog(
        title = stringResource(R.string.copy_from),
        body = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                LazyColumn(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier.overscroll(rememberOverscrollEffect()),
                    state = rememberLazyListState(),
                    userScrollEnabled = true,
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                ) {
                    items(tunnels, key = { it.id }) { tunnel ->
                        SurfaceRow(
                            title = tunnel.name,
                            trailing =
                                if (selectedTunnelId == tunnel.id) {
                                    {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else null,
                            onClick = { onSelect(tunnel.id) },
                        )
                    }
                }
            }
        },
        onAttest = { onAttest(selectedTunnelId) },
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.copy),
    )
}
