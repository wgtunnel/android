package com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.StatisticRefresh
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dropdown.LabelledDropdown
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.viewmodel.MonitoringViewModel
import org.koin.androidx.compose.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun MonitoringScreen(viewModel: MonitoringViewModel = koinViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.collectAsState()

    if (uiState.isLoading) return

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxSize(),
    ) {
        Column {
            GroupLabel(
                stringResource(R.string.statistics),
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
            LabelledDropdown(
                title = stringResource(R.string.refresh_rate),
                leading = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                currentValue = uiState.statisticRefresh,
                onSelected = { selected ->
                    selected?.let { viewModel.onStatisticsIntervalChanged(it) }
                },
                options = StatisticRefresh.entries,
                optionToString = { (it ?: StatisticRefresh.BALANCED).asString(context) },
            )
        }
    }
}
