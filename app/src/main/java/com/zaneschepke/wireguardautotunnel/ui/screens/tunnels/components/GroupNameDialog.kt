package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox

@Composable
fun GroupNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(name) { isError = false }

    InfoDialog(
        onDismiss = onDismiss,
        title = title,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ConfigurationTextBox(
                    value = name,
                    label = stringResource(R.string.group_name),
                    hint = stringResource(R.string.add_group),
                    onValueChange = { name = it },
                    isError = isError,
                )
            }
        },
        confirmText = stringResource(R.string.okay),
        onAttest = {
            if (name.isBlank()) {
                isError = true
            } else {
                onConfirm(name.trim())
            }
        },
    )
}
