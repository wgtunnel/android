package com.zaneschepke.wireguardautotunnel.ui.common.snackbar

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberCustomSnackbarState(): CustomSnackbarState {
    return remember { CustomSnackbarState() }
}

class CustomSnackbarState {
    private val _snackbars = Channel<SnackbarInfo>(Channel.BUFFERED)
    val snackbars: Channel<SnackbarInfo> = _snackbars

    private var currentSnackbar by mutableStateOf<SnackbarInfo?>(null)
    private var isShowing by mutableStateOf(false)

    fun showSnackbar(info: SnackbarInfo) {
        _snackbars.trySend(info)
    }

    fun dismissCurrent() {
        currentSnackbar = null
        isShowing = false
    }

    @Composable
    fun SnackbarHost(
        modifier: Modifier = Modifier,
        snackbar: @Composable (SnackbarInfo) -> Unit = { info ->
            CustomSnackBar(
                message = info.message,
                type = info.type,
                onDismiss = { dismissCurrent() },
                modifier = Modifier,
                containerColor = MaterialTheme.colorScheme.surface.copy(.1f),
            )
        },
    ) {
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            for (info in snackbars) {
                currentSnackbar = info
                isShowing = true

                scope.launch {
                    delay(info.durationMs)
                    if (currentSnackbar?.id == info.id) {
                        dismissCurrent()
                    }
                }

                while (isShowing && currentSnackbar?.id == info.id) {
                    delay(100)
                }
            }
        }

        currentSnackbar?.let { info ->
            if (isShowing) {
                Box(modifier = modifier) { snackbar(info) }
            }
        }
    }
}
