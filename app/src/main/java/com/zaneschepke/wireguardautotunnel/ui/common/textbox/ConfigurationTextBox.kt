package com.zaneschepke.wireguardautotunnel.ui.common.textbox

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ConfigurationTextBox(
    value: String,
    label: String,
    hint: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions(),
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions =
        KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable (Modifier) -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = MutableInteractionSource(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
) {

    CustomTextField(
        isError = isError,
        textStyle =
            MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.fillMaxWidth().heightIn(48.dp),
        value = value,
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        interactionSource = interactionSource,
        onValueChange = { onValueChange(it) },
        label = {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        placeholder = {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailing = trailing,
        supportingText = supportingText,
        leading = leading,
        readOnly = readOnly,
        enabled = enabled,
    )
}
