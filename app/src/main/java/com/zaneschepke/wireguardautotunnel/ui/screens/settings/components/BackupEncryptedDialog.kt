package com.zaneschepke.wireguardautotunnel.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.CustomTextField

@Composable
fun BackupEncryptionDialog(
    isRestore: Boolean,
    onConfirm: (encrypt: Boolean, password: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var encrypt by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswordError by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    InfoDialog(
        title =
            if (isRestore) {
                stringResource(R.string.restore)
            } else {
                stringResource(R.string.backup)
            },
        confirmText =
            if (isRestore) {
                stringResource(R.string.restore)
            } else {
                stringResource(R.string.backup)
            },
        onAttest = {
            if (!isRestore && encrypt && password != confirmPassword) {
                showPasswordError = true
                return@InfoDialog
            }
            if (encrypt && password.isBlank()) {
                return@InfoDialog
            }

            val finalPassword = if (encrypt) password else null
            onConfirm(encrypt, finalPassword)
        },
        onDismiss = onDismiss,
        body = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.encrypted))
                    ThemedSwitch(checked = encrypt, onClick = { encrypt = it })
                }

                if (encrypt) {
                    CustomTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            showPasswordError = false
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation =
                            if (passwordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailing = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector =
                                        if (passwordVisible) Icons.Outlined.VisibilityOff
                                        else Icons.Outlined.Visibility,
                                    contentDescription =
                                        if (passwordVisible) stringResource(R.string.hide_password)
                                        else stringResource(R.string.show_password),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (!isRestore) {
                        CustomTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                showPasswordError = false
                            },
                            label = { Text(stringResource(R.string.confirm_password)) },
                            visualTransformation =
                                if (confirmPasswordVisible) VisualTransformation.None
                                else PasswordVisualTransformation(),
                            trailing = {
                                IconButton(
                                    onClick = { confirmPasswordVisible = !confirmPasswordVisible }
                                ) {
                                    Icon(
                                        imageVector =
                                            if (confirmPasswordVisible) Icons.Outlined.VisibilityOff
                                            else Icons.Outlined.Visibility,
                                        contentDescription =
                                            if (confirmPasswordVisible)
                                                stringResource(R.string.hide_password)
                                            else stringResource(R.string.show_password),
                                    )
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                            isError = showPasswordError,
                        )
                    }

                    if (showPasswordError) {
                        Text(
                            text = stringResource(R.string.passwords_do_not_match),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
    )
}
