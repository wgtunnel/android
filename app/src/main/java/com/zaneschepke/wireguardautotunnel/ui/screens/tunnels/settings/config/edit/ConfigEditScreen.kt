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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HdrAuto
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.scrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
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
    val locale = Locale.current.platformLocale

    var showSelectionDialog by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    sharedViewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is LocalSideEffect.SaveChanges -> {
                if (uiState.isRunning) viewModel.setShowSaveModal(true) else viewModel.save()
            }
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

    if (uiState.ui.showSaveModal) {
        InfoDialog(
            onDismiss = { viewModel.setShowSaveModal(false) },
            onAttest = viewModel::save,
            title = stringResource(R.string.save_changes),
            body = {
                Text(
                    stringResource(
                        R.string.restart_message_template,
                        stringResource(R.string.tunnels).lowercase(locale),
                    )
                )
            },
            confirmText = stringResource(R.string._continue),
        )
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier =
            Modifier.fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .scrollbar(
                    state = scrollState.scrollIndicatorState,
                    orientation = Orientation.Vertical,
                ),
    ) {
        if (uiState.isGlobalConfig) {
            Column {
                SurfaceRow(
                    leading = {
                        Icon(ImageVector.vectorResource(R.drawable.host), contentDescription = null)
                    },
                    title = stringResource(R.string.dns_servers),
                    trailing = { modifier ->
                        ThemedSwitch(
                            checked = uiState.globalSettings.dnsEnabled,
                            onClick = { viewModel.setGlobalTunnelDnsEnabled(it) },
                            modifier = modifier,
                        )
                    },
                    onClick = {
                        viewModel.setGlobalTunnelDnsEnabled(!uiState.globalSettings.dnsEnabled)
                    },
                )
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.HdrAuto, contentDescription = null) },
                    title = stringResource(R.string.amnezia_configuration),
                    trailing = { modifier ->
                        ThemedSwitch(
                            checked = uiState.globalSettings.amneziaEnabled,
                            onClick = { viewModel.setGlobalAmneziaEnabled(it) },
                            modifier = modifier,
                        )
                    },
                    onClick = {
                        viewModel.setGlobalAmneziaEnabled(!uiState.globalSettings.amneziaEnabled)
                    },
                )
            }
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
        if (!uiState.isGlobalConfig)
            PeersSection(
                uiState = uiState,
                onPeerDropdownExpanded = viewModel::setPeerDropdownExpanded,
                onRemove = viewModel::onRemovePeer,
                onToggleLan = viewModel::onToggleLan,
                onUpdatePeer = viewModel::onUpdatePeer,
            )
        if (!uiState.isGlobalConfig) AddPeerButton { viewModel.onAddPeer() }
        if (LocalIsAndroidTV.current) {
            Spacer(modifier = Modifier.height(300.dp))
        }
    }
}
