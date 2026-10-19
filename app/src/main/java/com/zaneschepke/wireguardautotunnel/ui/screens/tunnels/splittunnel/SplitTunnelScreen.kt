package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.rememberRestartToApplyChanges
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components.SelectTunnelModal
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components.SplitTunnelContent
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitBehavior
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
    val showSystemApps by sharedViewModel.showSplitSystemApps.collectAsStateWithLifecycle()

    DisposableEffect(viewModel.tunnelId) {
        sharedViewModel.prepareSplitSystemApps(viewModel.tunnelId)
        onDispose { sharedViewModel.clearSplitSystemApps(viewModel.tunnelId) }
    }

    var showSelectionDialog by rememberSaveable { mutableStateOf(false) }

    val requestApply =
        rememberRestartToApplyChanges(
            needsRestart = uiState.needsRestart,
            onSave = viewModel::save,
        )

    sharedViewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is LocalSideEffect.SaveChanges -> requestApply()
            is LocalSideEffect.Modal.SelectTunnel -> showSelectionDialog = true
            else -> Unit
        }
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(waveSpeed = 60.dp, modifier = Modifier.size(48.dp))
        }
        return
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
        activeBehavior = uiState.activeBehavior,
        selectedPackages =
            when (uiState.activeBehavior) {
                SplitBehavior.INCLUDE -> uiState.includedPackages
                SplitBehavior.EXCLUDE -> uiState.excludedPackages
                SplitBehavior.ALL -> emptySet()
            },
        installedPackages = uiState.installedPackages,
        onActiveBehaviorChange = { viewModel.setActiveBehavior(it) },
        onAppSelectionToggle = { appPackage, enabled ->
            viewModel.togglePackage(appPackage, enabled)
        },
        showSystemApps = showSystemApps,
        globalOverride = uiState.globalOverride,
    )
}
