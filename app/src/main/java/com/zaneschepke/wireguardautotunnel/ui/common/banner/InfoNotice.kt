package com.zaneschepke.wireguardautotunnel.ui.common.banner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InfoNotice(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    detail: String? = null,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null,
    visible: Boolean = true,
) {
    AnimatedVisibility(visible = visible, enter = expandVertically(), exit = shrinkVertically()) {
        val colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
        val shape = RoundedCornerShape(8.dp)
        val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        val cardModifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = cardModifier,
                shape = shape,
                colors = colors,
                elevation = elevation,
            ) {
                NoticeBody(title, message, detail, actionLabel, onClick)
            }
        } else {
            Card(modifier = cardModifier, shape = shape, colors = colors, elevation = elevation) {
                NoticeBody(title, message, detail, actionLabel, onClick = null)
            }
        }
    }
}

@Composable
private fun NoticeBody(
    title: String,
    message: String?,
    detail: String?,
    actionLabel: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (actionLabel != null && onClick != null) {
            TextButton(onClick = onClick) {
                Text(
                    text = actionLabel,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
