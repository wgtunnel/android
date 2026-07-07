package com.zaneschepke.wireguardautotunnel.ui.common.functions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun rememberRotatingHint(hints: List<String>, key: Any, interval: Long = 5_000L): String {
    var index by remember(key) { mutableIntStateOf(0) }

    LaunchedEffect(key, hints) {
        while (isActive) {
            delay(interval.milliseconds)
            if (hints.isNotEmpty()) {
                index = (index + 1) % hints.size
            }
        }
    }

    return hints.getOrElse(index) { hints.firstOrNull().orEmpty() }
}
