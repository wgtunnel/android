package com.zaneschepke.wireguardautotunnel.ui.common.dialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.util.extensions.launchVpnSettings

@Composable
fun VpnDeniedDialog(show: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    if (show) {
        val alwaysOnDescription = buildAnnotatedString {
            append(stringResource(R.string.always_on_message))
            append(" ")
            withLink(
                LinkAnnotation.Clickable(
                    tag = "vpnSettings",
                    styles =
                        TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary)),
                ) {
                    context.launchVpnSettings()
                }
            ) {
                append(stringResource(id = R.string.vpn_settings))
            }
            append(" ")
            append(stringResource(R.string.always_on_message2))
            append(".")
        }
        InfoDialog(
            onDismiss = { onDismiss() },
            onAttest = { onDismiss() },
            title = stringResource(R.string.vpn_denied_dialog_title),
            body = {
                Text(
                    text = alwaysOnDescription,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.outline
                        ),
                )
            },
            confirmText = stringResource(R.string.okay),
        )
    }
}
