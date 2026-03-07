package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.data.entity.TunnelConfig
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.ui.common.security.SecureScreenFromRecording
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.config.components.AddPeerButton
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.config.components.InterfaceSection
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.config.components.PeersSection
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.ConfigProxy
import com.zaneschepke.wireguardautotunnel.ui.state.PeerProxy
import com.zaneschepke.wireguardautotunnel.viewmodel.ConfigViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun ConfigScreen(
    viewModel: ConfigViewModel,
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {

    val uiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (uiState.isLoading) return

    val locale = Locale.current.platformLocale

    var configProxy by remember {
        mutableStateOf(uiState.tunnel?.let { ConfigProxy.from(it.toAmConfig()) } ?: ConfigProxy())
    }

    var tunnelName by remember { mutableStateOf(uiState.tunnel?.name ?: "") }
    val isGlobalConfig = rememberSaveable { tunnelName == TunnelConfig.GLOBAL_CONFIG_NAME }

    val isTunnelNameTaken by
        remember(tunnelName) { derivedStateOf { uiState.unavailableNames.contains(tunnelName) } }

    sharedViewModel.collectSideEffect { sideEffect ->
        if (sideEffect is LocalSideEffect.SaveChanges)
            if (uiState.isRunning) viewModel.setShowSaveModal(true)
            else viewModel.saveConfigProxy(configProxy, tunnelName)
    }

    if (uiState.showSaveModal) {
        InfoDialog(
            onDismiss = { viewModel.setShowSaveModal(false) },
            onAttest = { viewModel.saveConfigProxy(configProxy, tunnelName) },
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

    SecureScreenFromRecording()

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        InterfaceSection(
            isGlobalConfig,
            configProxy = configProxy,
            uiState.isRunning,
            tunnelName,
            isTunnelNameTaken,
            onInterfaceChange = { configProxy = configProxy.copy(`interface` = it) },
            onTunnelNameChange = { tunnelName = it },
            onMimicQuic = {
                configProxy = configProxy.copy(`interface` = configProxy.`interface`.setQuicMimic())
            },
            onMimicDns = {
                configProxy = configProxy.copy(`interface` = configProxy.`interface`.setDnsMimic())
            },
            onMimicSip = {
                configProxy = configProxy.copy(`interface` = configProxy.`interface`.setSipMimic())
            },
        )
        if (!isGlobalConfig)
            PeersSection(
                configProxy,
                onRemove = {
                    configProxy =
                        configProxy.copy(
                            peers = configProxy.peers.toMutableList().apply { removeAt(it) }
                        )
                },
                onToggleLan = { index ->
                    configProxy =
                        configProxy.copy(
                            peers =
                                configProxy.peers.toMutableList().apply {
                                    val peer = get(index)
                                    val updated =
                                        if (peer.isLanExcluded()) peer.includeLan()
                                        else peer.excludeLan()
                                    set(index, updated)
                                }
                        )
                },
                onUpdatePeer = { peer, index ->
                    configProxy =
                        configProxy.copy(
                            peers = configProxy.peers.toMutableList().apply { set(index, peer) }
                        )
                },
            )
        if (!isGlobalConfig)
            AddPeerButton {
                configProxy = configProxy.copy(peers = configProxy.peers + PeerProxy())
            }
    }
}
