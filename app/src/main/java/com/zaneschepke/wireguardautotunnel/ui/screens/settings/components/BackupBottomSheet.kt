package com.zaneschepke.wireguardautotunnel.ui.screens.settings.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupBottomSheet(onBackup: () -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current

    CustomBottomSheet(
        listOf(
            SheetOption(
                ImageVector.vectorResource(R.drawable.database),
                stringResource(R.string.backup_application),
                onClick = onBackup,
            ),
            SheetOption(
                Icons.Outlined.Restore,
                stringResource(R.string.restore_application),
                onClick = onRestore,
            ),
        ),
    ) {
        onDismiss()
    }
}
