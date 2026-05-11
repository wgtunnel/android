package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R

@Composable
fun AddPeerButton(onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        TextButton(onClick = onClick) { Text(stringResource(R.string.add_peer)) }
    }
}
