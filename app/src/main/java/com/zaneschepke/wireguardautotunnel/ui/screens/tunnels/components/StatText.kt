package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Composable
fun StatText(text: String, style: TextStyle, color: Color) {
    Text(text = text.lowercase(), style = style, color = color)
}
