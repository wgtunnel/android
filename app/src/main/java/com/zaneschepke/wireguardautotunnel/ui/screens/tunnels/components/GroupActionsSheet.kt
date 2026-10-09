package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupActionsSheet(
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDeleteGroup: () -> Unit,
    onDeleteGroupAndTunnels: () -> Unit,
) {
    CustomBottomSheet(
        listOf(
            SheetOption(
                Icons.Outlined.Edit,
                stringResource(R.string.rename_group),
                onClick = {
                    onDismiss()
                    onRename()
                },
            ),
            SheetOption(
                Icons.Outlined.Download,
                stringResource(R.string.export_group),
                onClick = {
                    onDismiss()
                    onExport()
                },
            ),
            SheetOption(
                Icons.Outlined.Delete,
                stringResource(R.string.delete_group),
                onClick = {
                    onDismiss()
                    onDeleteGroup()
                },
            ),
            SheetOption(
                Icons.Outlined.DeleteForever,
                stringResource(R.string.delete_group_and_tunnels),
                onClick = {
                    onDismiss()
                    onDeleteGroupAndTunnels()
                },
            ),
        )
    ) {
        onDismiss()
    }
}
