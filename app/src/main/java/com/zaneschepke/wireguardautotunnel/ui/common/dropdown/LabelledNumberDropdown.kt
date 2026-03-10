package com.zaneschepke.wireguardautotunnel.ui.common.dropdown

import androidx.compose.runtime.*
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow

@Composable
fun <T> LabelledDropdown(
    title: String,
    description: (@Composable () -> Unit)? = null,
    leading: @Composable () -> Unit,
    enabled: Boolean = true,
    onSelected: (T?) -> Unit,
    options: List<T?>,
    currentValue: T?,
    optionToString: @Composable (T?) -> String,
) {
    var isDropDownExpanded by remember { mutableStateOf(false) }

    SurfaceRow(
        leading = leading,
        title = title,
        description = description,
        enabled = enabled,
        onClick = { isDropDownExpanded = true },
        trailing = {
            DropdownSelector(
                currentValue = currentValue,
                options = options,
                onValueSelected = { selected -> onSelected(selected) },
                isExpanded = isDropDownExpanded,
                onDismiss = { isDropDownExpanded = false },
                optionToString = optionToString,
            )
        },
    )
}
