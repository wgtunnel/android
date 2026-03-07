package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox

@Composable
fun UrlImportDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(url) { isError = false }

    InfoDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.add_from_url),
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(
                    stringResource(R.string.import_url_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ConfigurationTextBox(
                    value = url,
                    label = stringResource(R.string.enter_config_url),
                    hint = stringResource(R.string.example_import_url),
                    onValueChange = { url = it },
                    isError = isError,
                )
            }
        },
        confirmText = stringResource(R.string.okay),
        onAttest = {
            if (url.isNotBlank() && url.startsWith("https://")) {
                onConfirm(url)
            } else isError = true
        },
    )
}
