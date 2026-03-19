package com.zaneschepke.wireguardautotunnel.ui.common.dropdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R

@Composable
fun <T> DropdownSelector(
    currentValue: T?,
    options: List<T?>,
    onValueSelected: (T?) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    isExpanded: Boolean = false,
    enabled: Boolean = true,
    onDismiss: () -> Unit = {},
    optionToString: @Composable (T?) -> String = {
        it?.toString() ?: stringResource(R.string._default)
    },
) {
    val contentColor =
        if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label != null) label()
            Text(
                text = optionToString(currentValue),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.dropdown),
                tint = contentColor,
            )
        }

        DropdownMenu(
            modifier = modifier.heightIn(max = 250.dp),
            scrollState = rememberScrollState(),
            containerColor = MaterialTheme.colorScheme.surface,
            expanded = isExpanded,
            onDismissRequest = onDismiss,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionToString(option)) },
                    onClick = {
                        onValueSelected(option)
                        onDismiss()
                    },
                )
            }
        }
    }
}
