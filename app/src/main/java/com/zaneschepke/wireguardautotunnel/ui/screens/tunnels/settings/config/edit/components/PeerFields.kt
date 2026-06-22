package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox
import com.zaneschepke.wireguardautotunnel.ui.state.EditablePeer

@Composable
fun PeerFields(peer: EditablePeer, onPeerChange: (EditablePeer) -> Unit, showKey: Boolean) {
    val locale = Locale.current.platformLocale
    val keyboardController = LocalSoftwareKeyboardController.current
    val keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
    val keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
    var showPresharedKey by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(key1 = showKey) { showPresharedKey = showKey }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConfigurationTextBox(
            value = peer.publicKey,
            onValueChange = { onPeerChange(peer.copy(publicKey = it)) },
            label = stringResource(R.string.public_key),
            hint =
                stringResource(R.string.hint_template, stringResource(R.string.base64_key))
                    .lowercase(locale),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            visualTransformation =
                if (showPresharedKey) VisualTransformation.None else PasswordVisualTransformation(),
            value = peer.preSharedKey,
            enabled = true,
            hint = stringResource(R.string.optional),
            onValueChange = { onPeerChange(peer.copy(preSharedKey = it)) },
            label = stringResource(R.string.preshared_key),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
        )
        ConfigurationTextBox(
            value = peer.persistentKeepalive,
            onValueChange = { onPeerChange(peer.copy(persistentKeepalive = it)) },
            label = stringResource(R.string.persistent_keepalive),
            hint = stringResource(R.string.optional),
            trailing = {
                Text(
                    stringResource(R.string.seconds).lowercase(locale),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 10.dp),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
        ConfigurationTextBox(
            value = peer.endpoint,
            onValueChange = { onPeerChange(peer.copy(endpoint = it)) },
            label = stringResource(R.string.endpoint),
            hint =
                stringResource(R.string.hint_template, stringResource(R.string.server_port))
                    .lowercase(locale),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = peer.allowedIps,
            onValueChange = { onPeerChange(peer.copy(allowedIps = it)) },
            label = stringResource(R.string.allowed_ips),
            hint =
                stringResource(R.string.hint_template, stringResource(R.string.comma_separated))
                    .lowercase(locale),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
    }
}
