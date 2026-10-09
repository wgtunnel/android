package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelGroup
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption
import com.zaneschepke.wireguardautotunnel.ui.state.TunnelsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionActionsSheet(
    uiState: TunnelsUiState,
    onDismiss: () -> Unit,
    onSelectAll: () -> Unit,
    onMoveToGroup: () -> Unit,
    onUngroup: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRenameGroup: (TunnelGroup) -> Unit,
    onExportGroup: (TunnelGroup) -> Unit,
    onDeleteGroup: (TunnelGroup) -> Unit,
    onDeleteGroupAndTunnels: (TunnelGroup) -> Unit,
) {
    val singleGroup =
        uiState.selectedGroups.singleOrNull().takeIf { uiState.selectedTunnels.isEmpty() }
    val singleGroupHasTunnels =
        singleGroup != null && uiState.tunnels.any { it.groupId == singleGroup.id }

    CustomBottomSheet(
        buildList {
            add(
                SheetOption(
                    Icons.Outlined.SelectAll,
                    stringResource(R.string.select_all),
                    onClick = {
                        onDismiss()
                        onSelectAll()
                    },
                )
            )
            if (singleGroup != null) {
                add(
                    SheetOption(
                        Icons.Outlined.Edit,
                        stringResource(R.string.rename_group),
                        onClick = {
                            onDismiss()
                            onRenameGroup(singleGroup)
                        },
                    )
                )
                if (singleGroupHasTunnels) {
                    add(
                        SheetOption(
                            Icons.Outlined.Download,
                            stringResource(R.string.export_group),
                            onClick = {
                                onDismiss()
                                onExportGroup(singleGroup)
                            },
                        )
                    )
                }
                add(
                    SheetOption(
                        Icons.Outlined.Delete,
                        stringResource(R.string.delete_group),
                        onClick = {
                            onDismiss()
                            onDeleteGroup(singleGroup)
                        },
                    )
                )
                if (singleGroupHasTunnels) {
                    add(
                        SheetOption(
                            Icons.Outlined.DeleteForever,
                            stringResource(R.string.delete_group_and_tunnels),
                            onClick = {
                                onDismiss()
                                onDeleteGroupAndTunnels(singleGroup)
                            },
                        )
                    )
                }
            } else {
                if (uiState.canMoveToGroup) {
                    add(
                        SheetOption(
                            Icons.AutoMirrored.Outlined.DriveFileMove,
                            stringResource(R.string.move_to_group),
                            onClick = {
                                onDismiss()
                                onMoveToGroup()
                            },
                        )
                    )
                }
                if (uiState.canUngroup) {
                    add(
                        SheetOption(
                            Icons.Outlined.FolderOff,
                            stringResource(R.string.ungroup),
                            onClick = {
                                onDismiss()
                                onUngroup()
                            },
                        )
                    )
                }
                add(
                    SheetOption(
                        Icons.Outlined.Download,
                        stringResource(R.string.download),
                        onClick = {
                            onDismiss()
                            onExport()
                        },
                    )
                )
                if (uiState.canCopy) {
                    add(
                        SheetOption(
                            Icons.Outlined.CopyAll,
                            stringResource(R.string.copy),
                            onClick = {
                                onDismiss()
                                onCopy()
                            },
                        )
                    )
                }
                add(
                    SheetOption(
                        Icons.Outlined.Delete,
                        stringResource(R.string.delete),
                        onClick = {
                            onDismiss()
                            onDelete()
                        },
                    )
                )
            }
        }
    ) {
        onDismiss()
    }
}
