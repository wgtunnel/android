package com.zaneschepke.wireguardautotunnel.ui.screens.settings.recovery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.util.extensions.toAgoDisplay

@Composable
fun RecoveryDetailsDialog(
    eventCount: Int,
    lastEventMs: Long,
    onDismiss: () -> Unit,
) {
    val lastEvent =
        lastEventMs.takeIf { it > 0L }?.div(1000)?.toAgoDisplay()
            ?: stringResource(R.string.never).lowercase()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.recovery_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DescriptionText(stringResource(R.string.recovery_events, eventCount))
                DescriptionText(stringResource(R.string.recovery_last_event, lastEvent))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.okay)) } },
    )
}
