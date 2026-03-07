package com.zaneschepke.wireguardautotunnel.ui.common.sheet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomBottomSheet(options: List<SheetOption>, onDismiss: () -> Unit) {
    val isTv = LocalIsAndroidTV.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = isTv)
    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
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
