package com.zaneschepke.wireguardautotunnel.ui.screens.support.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.BuildConfig
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.UpdateDownloadState
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.util.Constants
import com.zaneschepke.wireguardautotunnel.util.extensions.canInstallPackages
import com.zaneschepke.wireguardautotunnel.util.extensions.openWebUrl
import com.zaneschepke.wireguardautotunnel.viewmodel.SupportViewModel
import org.orbitmvi.orbit.compose.collectAsState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateDialog(viewModel: SupportViewModel, context: Context, onPermissionNeeded: () -> Unit) {
    val supportState by viewModel.collectAsState()
    val download = supportState.download
    val presentation = rememberUpdateDownloadPresentation(download)
    val downloading = download as? UpdateDownloadState.Downloading
    val downloadedFile =
        (download as? UpdateDownloadState.Completed)?.file?.takeIf {
            it.name == supportState.appUpdate?.apkFileName
        }
    val isStandalone = BuildConfig.FLAVOR == Constants.STANDALONE_FLAVOR

    InfoDialog(
        // Closing the dialog leaves a running download alone, the cancel button stops it
        onDismiss = { viewModel.dismissUpdate() },
        confirmEnabled = !presentation.showProgress,
        dismissText =
            stringResource(if (downloading != null) R.string.cancel_download else R.string.cancel),
        onDismissButton = {
            if (downloading != null) viewModel.cancelDownload()
            viewModel.dismissUpdate()
        },
        onAttest = {
            if (!isStandalone) {
                supportState.appUpdate?.apkUrl?.let { context.openWebUrl(it) }
                return@InfoDialog
            }
            if (presentation.showProgress) return@InfoDialog
            if (context.canInstallPackages()) {
                viewModel.downloadAndInstall()
            } else {
                onPermissionNeeded()
            }
        },
        title = stringResource(R.string.update_available),
        body = {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val annotatedString = buildAnnotatedString {
                    append("${supportState.appUpdate?.version ?: ""}\n")
                    // Add clickable text for second line
                    withLink(
                        link =
                            LinkAnnotation.Clickable(
                                tag = stringResource(id = R.string.release_notes),
                                linkInteractionListener = { viewModel.viewReleaseNotes() },
                                styles =
                                    TextLinkStyles(
                                        style =
                                            SpanStyle(
                                                color = MaterialTheme.colorScheme.primary,
                                                textDecoration = TextDecoration.Underline,
                                            )
                                    ),
                            )
                    ) {
                        append(stringResource(R.string.release_notes))
                    }
                }

                Text(text = annotatedString)
                if (presentation.showProgress) {
                    val stroke = Stroke(cap = StrokeCap.Round, width = 4.0f)
                    val indicatorModifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    if (presentation.indeterminate) {
                        LinearWavyProgressIndicator(
                            modifier = indicatorModifier,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            stroke = stroke,
                            trackStroke = stroke,
                        )
                    } else {
                        LinearWavyProgressIndicator(
                            progress = { presentation.fraction },
                            modifier = indicatorModifier,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            stroke = stroke,
                            trackStroke = stroke,
                        )
                    }
                    Text(
                        text = downloadStatusText(presentation.asDownloading()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (downloading != null) {
                        Text(
                            text = stringResource(R.string.update_download_background_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmText =
            stringResource(
                when {
                    !isStandalone -> R.string.download
                    presentation.showProgress -> R.string.downloading
                    downloadedFile != null -> R.string.install
                    else -> R.string.download_and_install
                }
            ),
    )
}
