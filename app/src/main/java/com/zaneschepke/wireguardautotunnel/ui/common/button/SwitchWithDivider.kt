package com.zaneschepke.wireguardautotunnel.ui.common.button

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun SwitchWithDivider(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onClick: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    PreferenceTrailing() {
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
