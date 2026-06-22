package com.zaneschepke.wireguardautotunnel.ui.common.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.zaneschepke.wireguardautotunnel.R

@Composable
fun InfoDialog(
    onAttest: () -> Unit,
    onDismiss: () -> Unit,
    title: String,
    body: @Composable (() -> Unit),
    confirmText: String,
    modifier: Modifier = Modifier,
) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            AlertDialog(
                modifier = modifier,
                onDismissRequest = { onDismiss() },
                confirmButton = {
                    TextButton(onClick = { onAttest() }) { Text(text = confirmText) }
                },
                dismissButton = {
                    TextButton(onClick = { onDismiss() }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(text = title) },
                text = { body() },
                properties = DialogProperties(usePlatformDefaultWidth = true),
            )
        }
    }
}
