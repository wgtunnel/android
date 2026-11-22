package com.zaneschepke.wireguardautotunnel.ui.screens.pin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

/** True while the app is covered by the PIN lock */
val LocalAppLocked = compositionLocalOf { false }

/** Covers the app until the PIN is entered. */
@Composable
fun PinLockOverlay(active: Boolean, backKey: Any?, onBack: () -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                // Hit by every touch, so nothing beneath the lock receives one
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                // While fading out after unlock, screen readers move on to the app
                .then(if (active) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        PinLockScreen()
    }
    if (active) key(backKey) { BackHandler(onBack = onBack) }
}
