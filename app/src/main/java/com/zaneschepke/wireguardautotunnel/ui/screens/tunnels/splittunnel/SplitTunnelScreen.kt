package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components.SelectTunnelModal
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components.SplitTunnelContent
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitOption
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SplitTunnelViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitTunnelScreen(
    viewModel: SplitTunnelViewModel,
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {

    val tunnelsUiState by sharedViewModel.tunnelsUiState.collectAsStateWithLifecycle()
    val uiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    var showDialog by remember { mutableStateOf(false) }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(waveSpeed = 60.dp, modifier = Modifier.size(48.dp))
        }
        return
    }
    val tunnel = uiState.tunnel ?: return

    var effectiveTunnel by remember { mutableStateOf(tunnel) }

    val conf by remember(effectiveTunnel) { derivedStateOf { effectiveTunnel.toAmConfig() } }

    var splitConfig by
        remember(conf) {
            mutableStateOf(
                when {
                    conf.`interface`.excludedApplications.isNotEmpty() ->
                        Pair(SplitOption.EXCLUDE, conf.`interface`.excludedApplications.toSet())
                    conf.`interface`.includedApplications.isNotEmpty() ->
                        Pair(SplitOption.INCLUDE, conf.`interface`.includedApplications.toSet())
                    else -> Pair(SplitOption.ALL, emptySet<String>())
                }
            )
        }

    sharedViewModel.collectSideEffect { sideEffect ->
        if (sideEffect is LocalSideEffect.SaveChanges)
            viewModel.saveSplitTunnelSelection(splitConfig)
        if (sideEffect is LocalSideEffect.Modal.SelectTunnel) showDialog = true
    }

    SelectTunnelModal(
        showDialog,
        tunnelsUiState.tunnels,
        onAttest = { conf ->
            if (conf == null) return@SelectTunnelModal
            effectiveTunnel = conf
            showDialog = false
        },
        onDismiss = { showDialog = false },
    )

    SplitTunnelContent(
        splitConfig = splitConfig,
        installedPackages = uiState.installedPackages,
        onSplitOptionChange = { splitConfig = Pair(it, splitConfig.second) },
        onAppSelectionToggle = { appPackage, enabled ->
            val updated =
                splitConfig.second.toMutableSet().apply {
                    if (!enabled) remove(appPackage) else add(appPackage)
                }
            splitConfig = Pair(splitConfig.first, updated)
        },
    )
}
