package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.banner.InfoNotice
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.rememberRestartToApplyChanges
import com.zaneschepke.wireguardautotunnel.ui.common.scroll.appScrollbar
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components.AddPeerButton
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components.InterfaceSection
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components.PeersSection
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components.SelectTunnelModal
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.viewmodel.ConfigEditViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun ConfigEditScreen(
    viewModel: ConfigEditViewModel,
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {

    val uiState by viewModel.collectAsState()

    if (uiState.isLoading) return
    var showSelectionDialog by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val requestApply =
        rememberRestartToApplyChanges(
            needsRestart = uiState.isRunning,
            onSave = viewModel::save,
        )

    sharedViewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is LocalSideEffect.SaveChanges -> requestApply()
            is LocalSideEffect.Modal.SelectTunnel -> {
                showSelectionDialog = true
            }
            is LocalSideEffect.ShowSensitive -> {
                viewModel.toggleShowSensitiveData()
            }
            else -> Unit
        }
    }

    SelectTunnelModal(
        show = showSelectionDialog,
        tunnels = uiState.tunnels,
        selectedTunnelId = uiState.ui.selectedCopySourceTunnelId,
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

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier =
            Modifier.fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .appScrollbar(
                    state = scrollState.scrollIndicatorState,
                    orientation = Orientation.Vertical,
                ),
    ) {
        if (uiState.globalSettings.dnsEnabled || uiState.globalSettings.amneziaEnabled) {
            InfoNotice(
                title = stringResource(R.string.managed_globally),
                message = stringResource(R.string.global_override_notice),
            )
        }
        InterfaceSection(
            uiState = uiState,
            onInterfaceChange = viewModel::onInterfaceChange,
            onTunnelNameChange = viewModel::onTunnelNameChange,
            onMimic = viewModel::onMimicChange,
            onToggleScripts = viewModel::toggleShowScripts,
            onToggleAmneziaValues = viewModel::toggleShowAmneziaValues,
            onToggleDropdown = viewModel::setInterfaceDropdownExpanded,
            onToggleAmneziaCompat = viewModel::toggleAmneziaCompat,
        )
        PeersSection(
            uiState = uiState,
            onPeerDropdownExpanded = viewModel::setPeerDropdownExpanded,
            onRemove = viewModel::onRemovePeer,
            onToggleLan = viewModel::onToggleLan,
            onUpdatePeer = viewModel::onUpdatePeer,
        )
        AddPeerButton { viewModel.onAddPeer() }
        if (LocalIsAndroidTV.current) {
            Spacer(modifier = Modifier.height(300.dp))
        }
    }
}
