package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.parser.ActivePeer
import com.zaneschepke.wireguardautotunnel.util.extensions.abbreviateKey
import com.zaneschepke.wireguardautotunnel.util.extensions.toAgoDisplay

@Composable
fun PeerStatisticsSection(peer: ActivePeer) {
    val context = LocalContext.current
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.outline

    val rx = Formatter.formatFileSize(context, peer.rxBytes ?: 0L)

    val tx = Formatter.formatFileSize(context, peer.txBytes ?: 0L)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatText(
            text = stringResource(R.string.peer_template, peer.publicKey.abbreviateKey()),
            style = style,
            color = color,
        )

        TransferStatsRow(rx = rx, tx = tx, style = style, color = color)

        StatText(
            text =
                stringResource(
                    R.string.handshake_template,
                    peer.lastHandshakeSeconds?.toAgoDisplay()
                        ?: stringResource(R.string.never).lowercase(),
                ),
            style = style,
            color = color,
        )
    }
}
