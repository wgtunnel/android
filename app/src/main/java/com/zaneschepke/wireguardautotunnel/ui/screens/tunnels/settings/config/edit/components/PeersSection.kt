package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.state.ConfigProxy
import com.zaneschepke.wireguardautotunnel.ui.state.PeerProxy

@Composable
fun PeersSection(
    configProxy: ConfigProxy,
    onRemove: (index: Int) -> Unit,
    onToggleLan: (index: Int) -> Unit,
    onUpdatePeer: (PeerProxy, index: Int) -> Unit,
) {
    val isTv = LocalIsAndroidTV.current
    configProxy.peers.forEachIndexed { index, peer ->
        var isDropDownExpanded by remember { mutableStateOf(false) }
        var showPresharedKey by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                GroupLabel(
                    stringResource(R.string.peer),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row {
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                    if (isTv)
                        IconButton(onClick = { showPresharedKey = !showPresharedKey }) {
                            Icon(
                                Icons.Outlined.RemoveRedEye,
                                stringResource(R.string.show_password),
                            )
                        }
                    Column {
                        IconButton(onClick = { isDropDownExpanded = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.quick_actions),
                            )
                        }
                        DropdownMenu(
                            expanded = isDropDownExpanded,
                            onDismissRequest = { isDropDownExpanded = false },
                            modifier =
                                Modifier.shadow(12.dp).background(MaterialTheme.colorScheme.surface),
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (peer.isLanExcluded())
                                            stringResource(R.string.include_lan)
                                        else stringResource(R.string.exclude_lan)
                                    )
                                },
                                onClick = {
                                    onToggleLan(index)
                                    isDropDownExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            PeerFields(peer = peer, onPeerChange = { onUpdatePeer(it, index) }, showPresharedKey)
        }
    }
}
