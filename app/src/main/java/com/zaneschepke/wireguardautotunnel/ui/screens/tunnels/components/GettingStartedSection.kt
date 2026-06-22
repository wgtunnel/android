package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R

@Composable
fun GettingStartedSection(onClick: (url: String) -> Unit, modifier: Modifier = Modifier) {
    val url = stringResource(id = R.string.docs_url)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = 40.dp).fillMaxSize(),
    ) {
        // Reusable empty state visual + main message
        EmptyStateLottie(message = stringResource(R.string.no_tunnels_yet))

        Spacer(modifier = Modifier.height(16.dp))

        // Guidance text with link (specific to this screen)
        val fullText = stringResource(R.string.getting_started_guidance)
        val linkPhrase = stringResource(R.string.getting_started_guide_link)

        val guidance = buildAnnotatedString {
            val startIndex = fullText.indexOf(linkPhrase)
            if (startIndex >= 0) {
                append(fullText.substring(0, startIndex))
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "gettingStarted",
                        styles =
                            TextLinkStyles(
                                style = SpanStyle(color = MaterialTheme.colorScheme.primary)
                            ),
                    ) {
                        onClick(url)
                    }
                ) {
                    append(linkPhrase)
                }
                append(fullText.substring(startIndex + linkPhrase.length))
            } else {
                append(fullText)
            }
        }

        Text(
            text = guidance,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
            textAlign = TextAlign.Center,
        )
    }
}
