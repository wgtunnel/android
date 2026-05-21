package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zaneschepke.tunnel.state.ActiveTunnel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

@Composable
fun TunnelStatisticsRow(activeTunnel: ActiveTunnel) {
    val now by
        produceState(System.currentTimeMillis()) {
            while (true) {
                delay(1.seconds)
                value = System.currentTimeMillis()
            }
        }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TunnelOverviewSection(activeTunnel = activeTunnel, now = now)

        activeTunnel.activeConfig?.peers?.forEach { peer -> PeerStatisticsSection(peer) }
    }
}
