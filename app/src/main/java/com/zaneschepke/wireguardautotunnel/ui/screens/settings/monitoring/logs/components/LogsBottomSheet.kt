package com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring.logs.components

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberFileExportLauncherForResult
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.CustomBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.common.sheet.SheetOption
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.FileUtils
import com.zaneschepke.wireguardautotunnel.util.extensions.hasSAFSupport
import com.zaneschepke.wireguardautotunnel.util.extensions.toUserFriendlyTimestamp
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsBottomSheet(
    onExport: (Uri) -> Unit,
    onDelete: () -> Unit,
    onCanceled: () -> Unit,
    onUnsupported: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val exportLauncher =
        rememberFileExportLauncherForResult(
            mimeType = FileUtils.ZIP_FILE_MIME_TYPE,
            onSuccess = { uri -> onExport(uri) },
            onCanceled = onCanceled,
            onUnsupported = onUnsupported,
        )

    fun handleFileExport() {
        if (context.hasSAFSupport(FileUtils.ZIP_FILE_MIME_TYPE)) {
            val timestamp = Instant.now().toUserFriendlyTimestamp()
            val fileName =
                "${Constants.BASE_LOG_FILE_NAME}_${timestamp}_${BuildConfig.VERSION_NAME}_${BuildConfig.FLAVOR}.zip"

            exportLauncher.launch(fileName)
        } else {
            onUnsupported()
        }
    }

    CustomBottomSheet(
        listOf(
            SheetOption(
                Icons.Outlined.FolderZip,
                stringResource(R.string.export_logs),
                onClick = { handleFileExport() },
            ),
            SheetOption(
                Icons.Outlined.Delete,
                stringResource(R.string.delete_logs),
                onClick = onDelete,
            ),
        )
    ) {
        onDismiss()
    }
}
