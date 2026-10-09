package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption

@Composable
fun AddMenuSheet(onDismiss: () -> Unit, onNewGroupClick: () -> Unit, onImportClick: () -> Unit) {
    CustomBottomSheet(
        listOf(
            SheetOption(
                Icons.Outlined.FileOpen,
                stringResource(R.string.add_tunnel),
                onClick = {
                    onDismiss()
                    onImportClick()
                },
            ),
            SheetOption(
                Icons.Outlined.CreateNewFolder,
                stringResource(R.string.add_group),
                onClick = {
                    onDismiss()
                    onNewGroupClick()
                },
            ),
        )
    ) {
        onDismiss()
    }
}
