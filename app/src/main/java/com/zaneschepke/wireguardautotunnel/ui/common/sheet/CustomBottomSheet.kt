package com.zaneschepke.wireguardautotunnel.ui.common.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.screens.pin.LocalAppLocked

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomBottomSheet(
    options: List<SheetOption>,
    title: String? = null,
    description: String? = null,
    onDismiss: () -> Unit,
) {
    if (LocalAppLocked.current) return
    val isTv = LocalIsAndroidTV.current
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues =
                if (isTv) setOf(SheetValue.Hidden, SheetValue.Expanded)
                else setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
        )
    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        if (title != null || description != null) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                description?.let { DescriptionText(it) }
            }
            HorizontalDivider()
        }
        options.forEachIndexed { index, option ->
            SurfaceRow(
                title = option.label,
                onClick = option.onClick,
                leading = { Icon(imageVector = option.leadingIcon, contentDescription = null) },
                trailing =
                    if (option.selected) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = stringResource(R.string.selected),
                            )
                        }
                    } else null,
                description = option.description?.let { { DescriptionText(it) } },
            )
            if (index != options.size - 1) HorizontalDivider()
        }
    }
}

data class SheetOption(
    val leadingIcon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val selected: Boolean = false,
    val description: String? = null,
)
