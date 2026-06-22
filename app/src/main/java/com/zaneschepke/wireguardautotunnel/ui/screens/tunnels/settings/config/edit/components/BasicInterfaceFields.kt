package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.parser.crypto.Key
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberClipboardHelper
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox
import com.zaneschepke.wireguardautotunnel.ui.state.EditableInterface

@Composable
fun BasicInterfaceFields(
    interfaceState: EditableInterface,
    onInterfaceChange: (EditableInterface) -> Unit,
    showPrivateKey: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
) {
    val locale = Locale.current.platformLocale
    val clipboardManager = rememberClipboardHelper()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConfigurationTextBox(
            value = interfaceState.privateKey,
            onValueChange = { onInterfaceChange(interfaceState.copy(privateKey = it)) },
            label = stringResource(R.string.private_key),
            hint =
                stringResource(R.string.hint_template, stringResource(R.string.base64_key))
                    .lowercase(locale),
            visualTransformation =
                if (showPrivateKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailing =
                if (!LocalIsAndroidTV.current) {
                    {
                        IconButton(
                            onClick = {
                                val privateKey = Key.generatePrivateKey()
                                val publicKey = Key.generatePublicKey(privateKey)
                                onInterfaceChange(
                                    interfaceState.copy(
                                        privateKey = privateKey.toBase64(),
                                        publicKey = publicKey.toBase64(),
                                    )
                                )
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                stringResource(R.string.rotate_keys),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                } else null,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
            keyboardActions = keyboardActions,
            singleLine = true,
        )

        ConfigurationTextBox(
            value = interfaceState.publicKey,
            onValueChange = { onInterfaceChange(interfaceState.copy(publicKey = it)) },
            label = stringResource(R.string.public_key),
            hint =
                stringResource(R.string.hint_template, stringResource(R.string.base64_key))
                    .lowercase(locale),
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailing = {
                IconButton(
                    onClick = { clipboardManager.copy(interfaceState.publicKey) }
                ) { // reuse your clipboard helper
                    Icon(Icons.Rounded.ContentCopy, stringResource(R.string.copy_public_key))
                }
            },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )

        ConfigurationTextBox(
            value = interfaceState.addresses,
            onValueChange = { onInterfaceChange(interfaceState.copy(addresses = it)) },
            label = stringResource(R.string.addresses),
            hint =
                stringResource(R.string.hint_template, stringResource(R.string.comma_separated))
                    .lowercase(locale),
            modifier = Modifier.fillMaxWidth(),
        )

        ConfigurationTextBox(
            value = interfaceState.listenPort,
            onValueChange = { onInterfaceChange(interfaceState.copy(listenPort = it)) },
            label = stringResource(R.string.listen_port),
            hint = stringResource(R.string.random),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
