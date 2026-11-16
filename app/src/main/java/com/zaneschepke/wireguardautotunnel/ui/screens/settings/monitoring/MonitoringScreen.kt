package com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.SeamlessRecoveryBounceDelay
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dropdown.LabeledDropdown
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.common.text.ScreenSection
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring.components.StatisticRefreshBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.recovery.components.RecoveryDetailsDialog
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.viewmodel.MonitoringViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SettingsViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun MonitoringScreen(
    viewModel: MonitoringViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.collectAsState()
    val settingsState by settingsViewModel.collectAsState()
    var showRefreshRateSheet by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }

    if (uiState.isLoading || settingsState.isLoading) return

    sharedViewModel.collectSideEffect { sideEffect ->
        if (sideEffect is LocalSideEffect.Modal.RecoveryDetails) showDetails = true
    }

    if (showDetails) {
        RecoveryDetailsDialog(
            eventCount = settingsState.recoveryEventCount,
            lastEventMs = settingsState.lastRecoveryEventMs,
            onDismiss = { showDetails = false },
        )
    }

    if (showRefreshRateSheet) {
        StatisticRefreshBottomSheet(
            onRefreshChange = viewModel::onStatisticsIntervalChanged,
            refresh = uiState.statisticRefresh,
        ) {
            showRefreshRateSheet = false
        }
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxSize(),
    ) {
        Column {
            GroupLabel(
                stringResource(MonitoringSection.STATISTICS.title),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Analytics, contentDescription = null) },
                title = stringResource(R.string.tunnel_statistics),
                trailing = {
                    ThemedSwitch(
                        checked = uiState.tunnelStatisticsEnabled,
                        onClick = { viewModel.onLiveTunnelStatisticsChanged(it) },
                    )
                },
                onClick = {
                    viewModel.onLiveTunnelStatisticsChanged(!uiState.tunnelStatisticsEnabled)
                },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                trailing = { modifier ->
                    IconButton(onClick = { showRefreshRateSheet = true }, modifier) {
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(R.string.select),
                        )
                    }
                },
                title = stringResource(R.string.refresh_rate),
                description = { DescriptionText(uiState.statisticRefresh.asString(context)) },
                onClick = { showRefreshRateSheet = true },
            )
        }
        Column {
            val bounceDelay =
                SeamlessRecoveryBounceDelay.fromSeconds(
                    settingsState.settings.seamlessRecoveryBounceDelaySec,
                )
            GroupLabel(
                stringResource(MonitoringSection.RECOVERY.title),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Autorenew, contentDescription = null) },
                title = stringResource(R.string.seamless_recovery),
                trailing = { modifier ->
                    ThemedSwitch(
                        checked = settingsState.settings.seamlessRecoveryEnabled,
                        onClick = { settingsViewModel.setSeamlessRecovery(enabled = it) },
                        modifier = modifier,
                    )
                },
                description = { DescriptionText(stringResource(R.string.seamless_recovery_desc)) },
                onClick = {
                    settingsViewModel.setSeamlessRecovery(
                        !settingsState.settings.seamlessRecoveryEnabled,
                    )
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
                    selected?.let { settingsViewModel.setSeamlessRecoveryBounceDelay(it.seconds) }
                },
                options = SeamlessRecoveryBounceDelay.entries,
                optionToString = { (it ?: SeamlessRecoveryBounceDelay.THIRTY).asString() },
            )
        }
    }
}

/** Sections of the Monitoring and recovery screen, in the order they appear. */
enum class MonitoringSection(@StringRes override val title: Int) : ScreenSection {
    STATISTICS(R.string.statistics),
    RECOVERY(R.string.tunnel_recovery),
}
