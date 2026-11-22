package com.zaneschepke.wireguardautotunnel.ui.common.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.StrictSplitNotice
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.screens.pin.LocalAppLocked
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictSplitNoticeSheet(
    notice: StrictSplitNotice,
    onConfirm: () -> Unit,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (LocalAppLocked.current) return
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val scope = rememberCoroutineScope()
    // Animate the sheet away before acting, so it doesn't vanish in one frame
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }
    val enabled = notice == StrictSplitNotice.ENABLED

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Text(
                stringResource(
                    if (enabled) R.string.strict_split_notice_title
                    else R.string.strict_split_suggest_title,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(
                    if (enabled) R.string.strict_split_notice_body
                    else R.string.strict_split_suggest_body,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            DescriptionText(
                stringResource(
                    if (enabled) R.string.strict_split_notice_limits
                    else R.string.strict_split_suggest_limits,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { close(onSecondary) }) {
                    Text(
                        stringResource(
                            if (enabled) R.string.strict_split_notice_settings
                            else R.string.no_thanks,
                        ),
                    )
                }
                Button(onClick = { close(onConfirm) }) {
                    Text(
                        stringResource(
                            if (enabled) R.string.okay else R.string.strict_split_suggest_enable,
                        ),
                    )
                }
            }
        }
    }
}
