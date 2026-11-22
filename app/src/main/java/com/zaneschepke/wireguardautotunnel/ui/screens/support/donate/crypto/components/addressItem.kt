package com.zaneschepke.wireguardautotunnel.ui.screens.support.donate.crypto.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.screens.support.donate.crypto.Address

@Composable
fun AddressItem(address: Address, onClick: (address: String) -> Unit) {
    val context = LocalContext.current
    val walletAddress = context.getString(address.address)
    var expand by rememberSaveable { mutableStateOf(false) }
    SurfaceRow(
        leading = {
            Image(
                painter = painterResource(id = address.icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        trailing = {
            IconButton(onClick = { onClick(walletAddress) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
            }
        },
        title = stringResource(address.name),
        description = {
            Text(
                text = walletAddress,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.outline,
                    ),
                maxLines = if (expand) Int.MAX_VALUE else 1,
                overflow = if (expand) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize(),
            )
        },
        onClick = { expand = !expand },
    )
}
