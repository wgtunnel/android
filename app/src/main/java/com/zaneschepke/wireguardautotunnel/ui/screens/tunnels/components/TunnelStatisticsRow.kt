package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wgtunnel.backend.state.ActiveTunnel
import com.zaneschepke.wireguardautotunnel.util.extensions.proxyStatusTexts
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

@Composable
fun TunnelStatisticsRow(activeTunnel: ActiveTunnel) {
    val context = LocalContext.current
    val now by
        produceState(System.currentTimeMillis()) {
            while (true) {
                delay(1.seconds)
                value = System.currentTimeMillis()
            }
        }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TunnelOverviewSection(activeTunnel = activeTunnel, now = now)

        activeTunnel.activeConfig?.peers?.forEach { peer -> PeerStatisticsSection(peer, now) }

        val proxyTexts = activeTunnel.proxyStatusTexts(context)
        if (proxyTexts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                proxyTexts.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
