package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelGroup
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToGroupSheet(
    groups: List<TunnelGroup>,
    onDismiss: () -> Unit,
    onSelect: (TunnelGroup) -> Unit,
    onNewGroup: () -> Unit,
) {
    CustomBottomSheet(
        listOf(
            SheetOption(
                Icons.Outlined.CreateNewFolder,
                stringResource(R.string.add_group_ellipsis),
                onClick = {
                    onDismiss()
                    onNewGroup()
                },
            )
        ) +
            groups
                .sortedBy { it.name.lowercase() }
                .map { group ->
                    SheetOption(
                        Icons.Outlined.Folder,
                        group.name,
                        onClick = {
                            onDismiss()
                            onSelect(group)
                        },
                    )
                }
    ) {
        onDismiss()
    }
}
