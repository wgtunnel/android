package com.zaneschepke.wireguardautotunnel.ui.common.button

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun SwitchWithDivider(
    checked: Boolean,
    onClick: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VerticalDivider(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outline,
        )
        Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures {} }) {
            ThemedSwitch(
                checked = checked,
                onClick = onClick,
                enabled = enabled,
                modifier = modifier,
            )
        }
    }
}
