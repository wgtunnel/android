package com.zaneschepke.wireguardautotunnel.ui.screens.support.license

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.ui.screens.support.license.components.LicenseList
import com.zaneschepke.wireguardautotunnel.viewmodel.LicenseViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LicenseScreen(viewModel: LicenseViewModel = koinViewModel()) {
    val licenseUiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (licenseUiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(waveSpeed = 60.dp, modifier = Modifier.size(48.dp))
        }
    } else {
        LicenseList(licenseUiState.licenses)
    }
}
