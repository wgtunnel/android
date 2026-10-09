package com.zaneschepke.wireguardautotunnel.ui.common.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.zaneschepke.wireguardautotunnel.R

@Composable
fun rememberRestartToApplyChanges(
    needsRestart: Boolean,
    onSave: (restart: Boolean) -> Unit,
): () -> Unit {
    var show by rememberSaveable { mutableStateOf(false) }
    val latestOnSave by rememberUpdatedState(onSave)
    val latestNeedsRestart by rememberUpdatedState(needsRestart)

    if (show) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy()) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                AlertDialog(
                    onDismissRequest = { show = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                show = false
                                latestOnSave(true)
                            }
                        ) {
                            Text(stringResource(R.string.save_and_restart))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                show = false
                                latestOnSave(false)
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.restart_required)) },
                    text = { Text(stringResource(R.string.restart_to_apply_message)) },
                    properties = DialogProperties(usePlatformDefaultWidth = true),
                )
            }
        }
    }

    return { if (latestNeedsRestart) show = true else latestOnSave(false) }
}
