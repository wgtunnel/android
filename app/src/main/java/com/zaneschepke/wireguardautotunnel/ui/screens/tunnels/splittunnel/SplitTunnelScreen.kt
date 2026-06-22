package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components.SelectTunnelModal
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components.SplitTunnelContent
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SplitTunnelViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitTunnelScreen(
    viewModel: SplitTunnelViewModel,
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {

    val uiState by viewModel.collectAsState()

    var showSelectionDialog by rememberSaveable { mutableStateOf(false) }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(waveSpeed = 60.dp, modifier = Modifier.size(48.dp))
        }
        return
    }

    sharedViewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is LocalSideEffect.SaveChanges -> viewModel.save()
            is LocalSideEffect.Modal.SelectTunnel -> showSelectionDialog = true
            else -> Unit
        }
    }

    SelectTunnelModal(
        show = showSelectionDialog,
        tunnels = uiState.tunnels,
        selectedTunnelId = uiState.selectedCopySourceTunnelId,
        onSelect = viewModel::selectCopySource,
        onAttest = {
            viewModel.applyCopySource()
            showSelectionDialog = false
        },
        onDismiss = {
            showSelectionDialog = false
            viewModel.selectCopySource(null)
        },
    )

    SplitTunnelContent(
        splitConfig = uiState.splitOption to uiState.selectedPackages,
        installedPackages = uiState.installedPackages,
        onSplitOptionChange = { viewModel.setSplitOption(it) },
        onAppSelectionToggle = { appPackage, enabled ->
            viewModel.togglePackage(appPackage, enabled)
        },
    )
}
