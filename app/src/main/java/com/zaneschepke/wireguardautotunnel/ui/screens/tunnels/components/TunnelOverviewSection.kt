package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.wireguardautotunnel.util.extensions.statusText
import com.zaneschepke.wireguardautotunnel.util.extensions.uptimeText

@Composable
fun TunnelOverviewSection(activeTunnel: ActiveTunnel, now: Long) {
    val context = LocalContext.current
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.outline

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = activeTunnel.statusText(context), style = style, color = color)

        activeTunnel.uptimeText(context, now)?.let { Text(text = it, style = style, color = color) }
    }
}
