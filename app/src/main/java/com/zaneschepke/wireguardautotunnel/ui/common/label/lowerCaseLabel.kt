package com.zaneschepke.wireguardautotunnel.ui.common.label

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale

@Composable
fun lowercaseLabel(text: String): String {
    val locale = Locale.current.platformLocale
    return text.lowercase(locale)
}
