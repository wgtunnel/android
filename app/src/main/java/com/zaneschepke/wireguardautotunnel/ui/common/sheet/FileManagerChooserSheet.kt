package com.zaneschepke.wireguardautotunnel.ui.common.sheet

import android.content.pm.PackageManager
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.functions.FileImportTarget
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.screens.pin.LocalAppLocked

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerChooserSheet(
    targets: List<FileImportTarget>,
    onDismiss: () -> Unit,
    onChoose: (FileImportTarget) -> Unit,
) {
    if (LocalAppLocked.current) return
    val isTv = LocalIsAndroidTV.current
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val listNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = available
        }
    }
    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = !isTv,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.choose_file_manager),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        HorizontalDivider()
        val maxListHeight =
            (LocalConfiguration.current.screenHeightDp.dp - 128.dp).coerceAtLeast(160.dp)
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(max = maxListHeight)
                    .nestedScroll(listNestedScrollConnection)
                    .verticalScroll(rememberScrollState()),
        ) {
            for (index in targets.indices) {
                val target = targets[index]
                FileManagerChooserRow(
                    target = target,
                    requestInitialFocus = index == 0,
                    onChoose = onChoose,
                )
                if (index != targets.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FileManagerChooserRow(
    target: FileImportTarget,
    requestInitialFocus: Boolean,
    onChoose: (FileImportTarget) -> Unit,
) {
    val context = LocalContext.current
    val icon =
        remember(target.packageName, target.activityName) {
            try {
                context.packageManager.getActivityIcon(target.componentName())
            } catch (_: PackageManager.NameNotFoundException) {
                AppCompatResources.getDrawable(context, R.drawable.resource_package)
            } catch (_: SecurityException) {
                AppCompatResources.getDrawable(context, R.drawable.resource_package)
            }
        }
    SurfaceRow(
        title = target.label,
        onClick = { onChoose(target) },
        requestInitialFocus = requestInitialFocus,
        leading = {
            Image(
                painter = rememberDrawablePainter(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        description =
            if (target.label != target.packageName) {
                { DescriptionText(target.packageName) }
            } else {
                null
            },
    )
}
