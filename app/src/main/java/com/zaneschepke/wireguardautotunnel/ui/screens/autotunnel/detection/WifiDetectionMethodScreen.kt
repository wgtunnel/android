package com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.detection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.domain.enums.WifiDetectionMethod
import com.zaneschepke.wireguardautotunnel.ui.common.button.IconSurfaceButton
import com.zaneschepke.wireguardautotunnel.util.extensions.asDescriptionString
import com.zaneschepke.wireguardautotunnel.util.extensions.asTitleString
import com.zaneschepke.wireguardautotunnel.viewmodel.AutoTunnelViewModel
import org.koin.androidx.compose.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun WifiDetectionMethodScreen(viewModel: AutoTunnelViewModel = koinViewModel()) {
    val context = LocalContext.current

    val autoTunnelState by viewModel.collectAsState()

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        enumValues<WifiDetectionMethod>().forEach {
            val title = it.asTitleString(context)
            val description = it.asDescriptionString(context)
            IconSurfaceButton(
                title = title,
                onClick = { viewModel.setWifiDetectionMethod(it) },
                selected = autoTunnelState.autoTunnelSettings.wifiDetectionMethod == it,
                description = description,
            )
        }
    }
}
