package com.zaneschepke.wireguardautotunnel.ui.screens.settings.logs.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaneschepke.logcatter.model.LogMessage
import com.zaneschepke.wireguardautotunnel.ui.common.functions.rememberClipboardHelper
import com.zaneschepke.wireguardautotunnel.ui.common.text.LogTypeLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

@Composable
fun LogItem(log: LogMessage) {
    val clipboardManager = rememberClipboardHelper()

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { clipboardManager.copy(log.toString()) },
                )
                .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        // Top row: Time + Level + Tag
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Timestamp
            Text(
                text = formatLogTime(log.time),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Gray,
            )

            // Log Level
            LogTypeLabel(color = Color(log.level.color())) {
                Text(
                    text = log.level.signifier,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Tag (truncated if too long)
            Text(
                text = log.tag,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // Message - full width + wrapping
        Text(
            text = log.message,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

private fun formatLogTime(timeString: String): String {
    return try {
        val instant = Instant.parse(timeString)
        timeFormatter.format(instant)
    } catch (e: Exception) {
        timeString.takeLast(12)
    }
}
