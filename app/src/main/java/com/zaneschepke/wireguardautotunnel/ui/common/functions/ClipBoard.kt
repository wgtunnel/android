package com.zaneschepke.wireguardautotunnel.ui.common.functions

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClipboardHelper(
    private val clipboard: Clipboard,
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    fun copy(text: String, label: String = "") {
        coroutineScope.launch(dispatcher) {
            val clipData = ClipData.newPlainText(label, text)
            clipboard.setClipEntry(ClipEntry(clipData))
        }
    }

    fun paste(onResult: (String?) -> Unit) {
        coroutineScope.launch(dispatcher) {
            val entry = clipboard.getClipEntry()
            val text = entry?.clipData?.getItemAt(0)?.text?.toString()
            onResult(text)
        }
    }
}

@Composable
fun rememberClipboardHelper(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): ClipboardHelper {
    val clipboard = LocalClipboard.current
    return remember(clipboard, coroutineScope) { ClipboardHelper(clipboard, coroutineScope) }
}
