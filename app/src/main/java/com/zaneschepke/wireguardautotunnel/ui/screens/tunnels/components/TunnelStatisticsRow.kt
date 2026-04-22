package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
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
import com.zaneschepke.wireguardautotunnel.util.extensions.localizedDuration
import com.zaneschepke.wireguardautotunnel.util.extensions.millisAgo
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TunnelStatisticsRow(
    activeTunnel: ActiveTunnel,
    pingEnabled: Boolean,
    showDetailedStats: Boolean,
) {
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
            // TODO add uptime
//            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(16.dp),
//                ) {
//                    Text(
//                        "uptime: ${tunnelState.uptime().localizedDuration(locale)}",
//                        style = textStyle,
//                        color = textColor,
//                    )
//                }
//            }

            config.peers.forEach { activePeer ->
                key(activePeer) {
                    val endpoint by remember(activePeer) {
                        derivedStateOf {
                            activePeer.endpoint
                        }
                    }
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
                    val handshake by
                        remember(activePeer) {
                            derivedStateOf {
                                activePeer.lastHandshakeSeconds?.let { lastHandshake ->
                                    if (lastHandshake == 0L) null
                                    else lastHandshake.millisAgo().localizedDuration(locale)
                                }
                            }
                        }
                    //TODO ping stuff
//                    val pingState by
//                        remember(tunnelState.pingStates) {
//                            derivedStateOf {
//                                tunnelState.pingStates?.getOrDefault(peerBase64, null)
//                            }
//                        }
//                    val lastPingedSeconds by
//                        remember(pingState, currentTimeMillis) {
//                            derivedStateOf {
//                                pingState
//                                    ?.lastSuccessfulPingMillis
//                                    ?.millisAgo()
//                                    ?.localizedDuration(locale)
//                            }
//                        }

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
                                "$handshakeText: ${handshake?.let { lowercaseLabel(it) } ?: neverText}",
                                style = textStyle,
                                color = textColor,
                            )
                        }
                        AnimatedVisibility(visible = endpoint != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    "$endpointText: $endpoint",
                                    style = textStyle,
                                    color = textColor,
                                )
                            }
                        }
//                        AnimatedVisibility(visible = pingState != null && pingEnabled) {
//                            pingState?.let {
//                                val reachableText =
//                                    lowercaseLabel(
//                                        stringResource(
//                                            R.string.reachable_template,
//                                            stringResource(
//                                                if (it.isReachable) R.string._true
//                                                else R.string._false
//                                            ),
//                                        )
//                                    )
//                                val pingTargetText =
//                                    lowercaseLabel(
//                                        stringResource(
//                                            R.string.ping_target_template,
//                                            it.pingTarget,
//                                        )
//                                    )
//                                val latencyText =
//                                    lowercaseLabel(
//                                        stringResource(R.string.latency_template, it.rttAvg)
//                                    )
//                                val jitterText =
//                                    lowercaseLabel(
//                                        stringResource(R.string.jitter_template, it.rttStddev)
//                                    )
//                                val packetsSentText =
//                                    lowercaseLabel(
//                                        stringResource(
//                                            R.string.packets_sent_template,
//                                            it.transmitted,
//                                        )
//                                    )
//                                val packetLossText =
//                                    lowercaseLabel(
//                                        stringResource(
//                                            R.string.packet_loss_template,
//                                            it.packetLoss,
//                                        )
//                                    )
//                                val pingSuccessText =
//                                    lowercaseLabel(
//                                        stringResource(
//                                            R.string.ping_success_template,
//                                            lastPingedSeconds?.let { sec ->
//                                                lowercaseLabel(sec)
//                                            } ?: neverText,
//                                        )
//                                    )
//                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
//                                    Row(
//                                        verticalAlignment = Alignment.CenterVertically,
//                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
//                                    ) {
//                                        Text(
//                                            reachableText,
//                                            style = textStyle,
//                                            color = textColor,
//                                        )
//                                        Text(
//                                            pingTargetText,
//                                            style = textStyle,
//                                            color = textColor,
//                                        )
//                                    }
//                                    if (showDetailedStats) {
//                                        Row(
//                                            verticalAlignment = Alignment.CenterVertically,
//                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
//                                        ) {
//                                            Text(
//                                                latencyText,
//                                                style = textStyle,
//                                                color = textColor,
//                                            )
//                                            Text(
//                                                jitterText,
//                                                style = textStyle,
//                                                color = textColor,
//                                            )
//                                        }
//                                        Row(
//                                            verticalAlignment = Alignment.CenterVertically,
//                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
//                                        ) {
//                                            Text(
//                                                packetsSentText,
//                                                style = textStyle,
//                                                color = textColor,
//                                            )
//                                            Text(
//                                                packetLossText,
//                                                style = textStyle,
//                                                color = textColor,
//                                            )
//                                        }
//                                        Row(
//                                            verticalAlignment = Alignment.CenterVertically,
//                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
//                                        ) {
//                                            Text(
//                                                pingSuccessText,
//                                                style = textStyle,
//                                                color = textColor,
//                                            )
//                                        }
//                                    }
//                                }
//                            }
//                        }
                    }
                }
            }
        }
    }
}
