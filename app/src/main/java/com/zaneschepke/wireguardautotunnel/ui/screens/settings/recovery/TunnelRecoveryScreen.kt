package com.zaneschepke.wireguardautotunnel.ui.screens.settings.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
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
import com.zaneschepke.wireguardautotunnel.domain.enums.SeamlessRecoveryBounceDelay
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dropdown.LabeledDropdown
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.recovery.components.RecoveryDetailsDialog
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.viewmodel.SettingsViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun TunnelRecoveryScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val uiState by viewModel.collectAsState()
    var showDetails by rememberSaveable { mutableStateOf(false) }

    if (uiState.isLoading) return

    sharedViewModel.collectSideEffect { sideEffect ->
        if (sideEffect is LocalSideEffect.Modal.RecoveryDetails) showDetails = true
    }

    if (showDetails) {
        RecoveryDetailsDialog(
            eventCount = uiState.recoveryEventCount,
            lastEventMs = uiState.lastRecoveryEventMs,
            onDismiss = { showDetails = false },
        )
    }

    val bounceDelay =
        SeamlessRecoveryBounceDelay.fromSeconds(uiState.settings.seamlessRecoveryBounceDelaySec)

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxSize(),
    ) {
        Column {
            GroupLabel(
                stringResource(R.string.tunnel_recovery),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Autorenew, contentDescription = null) },
                title = stringResource(R.string.seamless_recovery),
                trailing = { modifier ->
                    ThemedSwitch(
                        checked = uiState.settings.seamlessRecoveryEnabled,
                        onClick = { viewModel.setSeamlessRecovery(enabled = it) },
                        modifier = modifier,
                    )
                },
                description = { DescriptionText(stringResource(R.string.seamless_recovery_desc)) },
                onClick = {
                    viewModel.setSeamlessRecovery(!uiState.settings.seamlessRecoveryEnabled)
                },
            )
            LabeledDropdown(
                title = stringResource(R.string.recovery_bounce_delay),
                description = {
                    DescriptionText(stringResource(R.string.recovery_bounce_delay_desc))
                },
                leading = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                currentValue = bounceDelay,
                onSelected = { selected ->
                    selected?.let { viewModel.setSeamlessRecoveryBounceDelay(it.seconds) }
                },
                options = SeamlessRecoveryBounceDelay.entries,
                optionToString = { (it ?: SeamlessRecoveryBounceDelay.THIRTY).asString() },
            )
        }
    }
}
