package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.state.ConfigUiState
import com.zaneschepke.wireguardautotunnel.ui.state.EditablePeer

@Composable
fun PeersSection(
    uiState: ConfigUiState,
    onRemove: (index: Int) -> Unit,
    onPeerDropdownExpanded: (Boolean) -> Unit,
    onToggleLan: (index: Int) -> Unit,
    onUpdatePeer: (EditablePeer, index: Int) -> Unit,
) {
    uiState.draft.config.peers.forEachIndexed { index, peer ->
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
                    Column {
                        IconButton(
                            onClick = { onPeerDropdownExpanded(!uiState.ui.isPeerDropdownExpanded) }
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.quick_actions),
                            )
                        }
                        DropdownMenu(
                            expanded = uiState.ui.isPeerDropdownExpanded,
                            onDismissRequest = {
                                onPeerDropdownExpanded(!uiState.ui.isPeerDropdownExpanded)
                            },
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
                                    onPeerDropdownExpanded(false)
                                },
                            )
                        }
                    }
                }
            }
            PeerFields(
                peer = peer,
                onPeerChange = { onUpdatePeer(it, index) },
                uiState.ui.showSensitiveData,
            )
        }
    }
}
