package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.scrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberClipboardHelper
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.components.QrCodeDialog
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.theme.ConfigHeaderColor
import com.zaneschepke.wireguardautotunnel.ui.theme.ConfigKeyColor
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.isTextTooLargeForQr
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.TunnelViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun ConfigScreen(
    viewModel: TunnelViewModel,
    liveConfig: Boolean,
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {

    val context = LocalContext.current
    val clipboard = rememberClipboardHelper()
    val uiState by viewModel.collectAsState()
    var showKeys by rememberSaveable { mutableStateOf(false) }

    if (uiState.isLoading) return
    val tunnel = uiState.tunnel ?: return

    var showQrModal by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val rawConfig by
        remember(liveConfig, uiState.activeConfig, uiState.tunnel?.quickConfig) {
            derivedStateOf {
                if (liveConfig) {
                    uiState.activeConfig?.asQuickString() ?: uiState.tunnel?.quickConfig ?: ""
                } else {
                    uiState.tunnel?.quickConfig ?: ""
                }
            }
        }

    sharedViewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is LocalSideEffect.Modal.QR -> {
                if (tunnel.quickConfig.isTextTooLargeForQr()) {
                    sharedViewModel.postSideEffect(
                        GlobalSideEffect.Snackbar(
                            StringValue.StringResource(R.string.text_too_large_for_qr),
                            ToastType.Error,
                        )
                    )
                } else {
                    showQrModal = true
                }
            }
            is LocalSideEffect.ShowSensitive -> showKeys = !showKeys
            is LocalSideEffect.CopyToClipboard -> clipboard.copy(rawConfig)
            else -> Unit
        }
    }

    if (showQrModal) {
        QrCodeDialog(tunnelConfig = tunnel, onDismiss = { showQrModal = false })
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(scrollState)
                .scrollbar(
                    state = scrollState.scrollIndicatorState,
                    orientation = Orientation.Vertical,
                ),
    ) {
        val displayText by
            remember(rawConfig, showKeys) { derivedStateOf { maskSensitive(rawConfig, showKeys) } }
        val annotated by
            remember(displayText) { derivedStateOf { buildConfigAnnotatedString(displayText) } }

        SelectionContainer {
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

fun buildConfigAnnotatedString(text: String): AnnotatedString {
    val headerRegex = "\\[(Interface|Peer)]".toRegex()
    val keyRegex = "(?m)^[a-zA-Z0-9]+(?=\\s*=)".toRegex()
    val commentRegex = "#.*".toRegex()

    val builder = AnnotatedString.Builder(text)

    // Headers
    headerRegex.findAll(text).forEach {
        builder.addStyle(
            SpanStyle(color = ConfigHeaderColor, fontWeight = FontWeight.Bold),
            it.range.first,
            it.range.last + 1,
        )
    }

    // Keys
    keyRegex.findAll(text).forEach {
        builder.addStyle(SpanStyle(color = ConfigKeyColor), it.range.first, it.range.last + 1)
    }

    // Comments
    commentRegex.findAll(text).forEach { match ->
        val trimmed = match.value.trimEnd()
        if (trimmed.isNotEmpty()) {
            builder.addStyle(
                SpanStyle(color = Color.Gray, fontStyle = FontStyle.Italic),
                match.range.first,
                match.range.first + trimmed.length,
            )
        }
    }

    return builder.toAnnotatedString()
}

fun maskSensitive(text: String, showSensitive: Boolean): String {
    if (showSensitive) return text

    val sensitiveKeys = listOf("PrivateKey", "PresharedKey", "PreSharedKey")

    // WireGuard keys are 44 chars
    val maskedKey = "•".repeat(44)

    return text.lines().joinToString("\n") { line ->
        val isSensitive = sensitiveKeys.any { line.trimStart().startsWith("$it =") }

        if (isSensitive) {
            val prefix = line.substringBefore("=") + "= "
            "$prefix$maskedKey"
        } else {
            line
        }
    }
}
