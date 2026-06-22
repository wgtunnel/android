package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.zaneschepke.wireguardautotunnel.MainActivity
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.util.extensions.setScreenBrightness
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrErrorCorrectionLevel
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrOptions
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.circle
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

@Composable
fun QrCodeDialog(tunnelConfig: TunnelConfig, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    // Handle screen brightness
    DisposableEffect(Unit) {
        activity?.setScreenBrightness(1.0f)
        onDispose { activity?.setScreenBrightness(-1f) }
    }

    QrCodeAlertDialog(tunnelConfig = tunnelConfig, onDismiss = onDismiss)
}

@Composable
private fun QrCodeAlertDialog(tunnelConfig: TunnelConfig, onDismiss: () -> Unit) {
    AlertDialog(
        containerColor = Color.White,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done), color = MaterialTheme.colorScheme.surface)
            }
        },
        title = {
            Text(
                text = tunnelConfig.name,
                color = Color.Black,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = { QrCodeContent(tunnelConfig = tunnelConfig, onDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = true),
    )
}

@Composable
private fun QrCodeContent(tunnelConfig: TunnelConfig, onDismiss: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
    ) {
        val qrCodePainter =
            rememberQrCodePainter(data = tunnelConfig.quickConfig, options = createQrOptions())
        Image(
            painter = qrCodePainter,
            contentDescription = stringResource(R.string.show_qr),
            modifier =
                Modifier.size(300.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
                    .background(Color.White),
        )
    }
}

private fun createQrOptions(): QrOptions = QrOptions {
    shapes {
        darkPixel = QrPixelShape.circle()
        ball = QrBallShape.circle()
        frame = QrFrameShape.roundCorners(0.2f)
    }
    colors {
        dark = QrBrush.solid(Color.Black)
        frame = QrBrush.solid(Color.Black)
        ball = QrBrush.solid(Color.Black)
    }
    errorCorrectionLevel = QrErrorCorrectionLevel.Medium
}
