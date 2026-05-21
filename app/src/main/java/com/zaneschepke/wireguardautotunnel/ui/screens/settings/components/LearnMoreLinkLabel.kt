package com.zaneschepke.wireguardautotunnel.ui.screens.settings.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import com.zaneschepke.wireguardautotunnel.R

@Composable
fun LearnMoreLinkLabel(onClick: (url: String) -> Unit, url: String) {
    val gettingStarted = buildAnnotatedString {
        withLink(
            LinkAnnotation.Clickable(
                tag = "details",
                styles =
                    TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary)),
            ) {
                onClick(url)
            }
        ) {
            append(stringResource(id = R.string.learn_more))
        }
    }
    Text(
        text = gettingStarted,
        style =
            MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                fontStyle = FontStyle.Italic,
            ),
    )
}
