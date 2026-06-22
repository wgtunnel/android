package com.zaneschepke.wireguardautotunnel.ui.common.textbox

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTextField(
    value: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle =
        MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
    label: @Composable (() -> Unit)? = null,
    containerColor: Color,
    onValueChange: (value: String) -> Unit = {},
    singleLine: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    keyboardActions: KeyboardActions = KeyboardActions(),
    supportingText: @Composable (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable ((Modifier) -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var isFocused by remember { mutableStateOf(false) }
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = value)) }
    val textFieldValue =
        remember(value, textFieldValueState) {
            if (textFieldValueState.text == value) {
                textFieldValueState
            } else {
                textFieldValueState.copy(text = value, selection = TextRange(value.length))
            }
        }

    val cursorBrush =
        if (isFocused) SolidColor(MaterialTheme.colorScheme.primary)
        else SolidColor(Color.Transparent)
    val editable = enabled && !readOnly
    val mainFocusRequester = remember { FocusRequester() }
    val trailingFocusRequester = remember { FocusRequester() }
    val disabledAlpha = 0.38f
    val disabledBorderAlpha = 0.12f
    val effectiveTextStyle =
        if (enabled) {
            textStyle
        } else {
            textStyle.copy(color = textStyle.color.copy(alpha = disabledAlpha))
        }

    BasicTextField(
        value = textFieldValue,
        textStyle = effectiveTextStyle,
        onValueChange = { newTextFieldValue ->
            textFieldValueState = newTextFieldValue
            if (value != newTextFieldValue.text) {
                onValueChange(newTextFieldValue.text)
            }
        },
        keyboardActions = keyboardActions,
        keyboardOptions = keyboardOptions,
        readOnly = readOnly,
        cursorBrush = cursorBrush,
        modifier =
            modifier
                .focusRequester(mainFocusRequester)
                .focusProperties {
                    canFocus = editable
                    if (canFocus && trailing != null) {
                        right = trailingFocusRequester
                    }
                }
                .onFocusChanged { focusState -> isFocused = focusState.isFocused },
        interactionSource = interactionSource,
        enabled = enabled,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
    ) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = it,
            placeholder = placeholder,
            contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 14.dp, bottom = 14.dp),
            leadingIcon = leading,
            trailingIcon =
                if (trailing != null) {
                    {
                        trailing(
                            Modifier.focusRequester(trailingFocusRequester).focusProperties {
                                if (editable) {
                                    left = mainFocusRequester
                                }
                            }
                        )
                    }
                } else null,
            singleLine = singleLine,
            supportingText = supportingText,
            colors =
                TextFieldDefaults.colors()
                    .copy(
                        disabledTextColor =
                            MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha),
                        disabledLabelColor =
                            MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha),
                        disabledPlaceholderColor =
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha),
                        disabledLeadingIconColor =
                            MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha),
                        disabledTrailingIconColor =
                            MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha),
                        disabledSupportingTextColor =
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha),
                        disabledContainerColor = containerColor,
                        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = containerColor,
                        unfocusedContainerColor = containerColor,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
            enabled = enabled,
            label = label,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = enabled,
                    isError = isError,
                    interactionSource = interactionSource,
                    colors =
                        TextFieldDefaults.colors()
                            .copy(
                                errorContainerColor = containerColor,
                                disabledLabelColor =
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha),
                                disabledContainerColor = containerColor,
                                disabledIndicatorColor =
                                    MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = disabledBorderAlpha
                                    ),
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = containerColor,
                                unfocusedContainerColor = containerColor,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary,
                            ),
                    shape = RoundedCornerShape(8.dp),
                    focusedBorderThickness = 0.5.dp,
                    unfocusedBorderThickness = 0.5.dp,
                    modifier = Modifier.height(48.dp),
                )
            },
        )
    }
}
