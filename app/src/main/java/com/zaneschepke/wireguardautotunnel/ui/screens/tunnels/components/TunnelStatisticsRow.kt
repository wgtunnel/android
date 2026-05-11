package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.tunnel.state.ActiveTunnel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.label.lowercaseLabel
import com.zaneschepke.wireguardautotunnel.util.extensions.abbreviateKey
import com.zaneschepke.wireguardautotunnel.util.extensions.toAgoDisplay
import com.zaneschepke.wireguardautotunnel.util.extensions.toUptimeDisplay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun TunnelStatisticsRow(activeTunnel: ActiveTunnel) {
    val context = LocalContext.current
    val textStyle = MaterialTheme.typography.bodySmall
    val textColor = MaterialTheme.colorScheme.outline
    val locale = Locale.current.platformLocale

    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L.milliseconds)
            currentTimeMillis = System.currentTimeMillis()
        }
    }
    val activeConfig = activeTunnel.activeConfig
    val peerText = lowercaseLabel(stringResource(R.string.peer))
    val handshakeText = lowercaseLabel(stringResource(R.string.handshake))
    val endpointText = lowercaseLabel(stringResource(R.string.endpoint))
    val neverText = lowercaseLabel(stringResource(R.string.never))

    activeConfig?.let { config ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            activeTunnel.uptime?.let { startTime ->
                val uptimeText by
                    remember(startTime, currentTimeMillis) {
                        derivedStateOf { startTime.toUptimeDisplay(currentTimeMillis) }
                    }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("uptime: $uptimeText", style = textStyle, color = textColor)
                    }
                }
            }

            config.peers.forEach { activePeer ->
                key(activePeer) {
                    val endpoint by remember(activePeer) { derivedStateOf { activePeer.endpoint } }
                    val formattedRx by
                        remember(activePeer) {
                            derivedStateOf {
                                activePeer.rxBytes?.let { Formatter.formatFileSize(context, it) }
                            }
                        }
                    val formattedTx by
                        remember(activePeer) {
                            derivedStateOf {
                                activePeer.txBytes?.let { Formatter.formatFileSize(context, it) }
                            }
                        }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                "$peerText: ${activePeer.publicKey.abbreviateKey()}",
                                style = textStyle,
                                color = textColor,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            formattedRx?.let {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.ArrowDownward,
                                        contentDescription = null,
                                        tint = textColor,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(it, style = textStyle, color = textColor)
                                }
                            }
                            formattedTx?.let {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.ArrowUpward,
                                        contentDescription = null,
                                        tint = textColor,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(it, style = textStyle, color = textColor)
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                "$handshakeText: ${activePeer.lastHandshakeSeconds?.toAgoDisplay() ?: neverText}",
                                style = textStyle,
                                color = textColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
