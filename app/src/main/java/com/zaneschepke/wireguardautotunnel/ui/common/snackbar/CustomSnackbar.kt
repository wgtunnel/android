package com.zaneschepke.wireguardautotunnel.ui.common.snackbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV

@Composable
fun CustomSnackBar(
    message: AnnotatedString,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    type: SnackbarType = SnackbarType.INFO,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val isTv = LocalIsAndroidTV.current
    val icon =
        when (type) {
            SnackbarType.INFO -> Icons.Rounded.Info
            SnackbarType.WARNING -> Icons.Rounded.Warning
            SnackbarType.THANK_YOU -> Icons.Outlined.Favorite
        }
    val iconDescription =
        when (type) {
            SnackbarType.INFO -> stringResource(R.string.info)
            SnackbarType.WARNING -> stringResource(R.string.warning)
            SnackbarType.THANK_YOU -> stringResource(R.string.thank_you)
        }

    Snackbar(
        containerColor = containerColor,
        modifier =
            modifier
                .wrapContentHeight(align = Alignment.Top)
                .padding(horizontal = if (isTv) 48.dp else 16.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .width(IntrinsicSize.Min)
                    .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Icon(
                    icon,
                    contentDescription = iconDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row {
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.stop),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
