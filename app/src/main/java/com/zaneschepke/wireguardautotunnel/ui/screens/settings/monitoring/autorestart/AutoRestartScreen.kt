package com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring.autorestart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.data.model.MaxAttemptsAction
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dropdown.LabelledDropdown
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.viewmodel.MonitoringViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun AutoRestartScreen(viewModel: MonitoringViewModel = koinViewModel()) {
    val navController = LocalNavController.current
    val uiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (uiState.isLoading) return

    val pingEnabled = uiState.monitoringSettings.isPingEnabled

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column {
            GroupLabel(
                stringResource(R.string.auto_restart),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (!pingEnabled) {
                Text(
                    text = stringResource(R.string.use_ping_for_detection_description),
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.outline
                        ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            LabelledDropdown(
                enabled = pingEnabled,
                title = stringResource(R.string.ping_failures_before_restart),
                leading = { Icon(Icons.Outlined.FilterAlt, contentDescription = null) },
                currentValue = uiState.monitoringSettings.pingFailuresBeforeRestart,
                onSelected = { selected ->
                    selected?.let { viewModel.setPingFailuresBeforeRestart(it) }
                },
                options = listOf(1, 2, 3, 4, 5),
                optionToString = { it?.toString() ?: stringResource(R.string._default) },
            )
            LabelledDropdown(
                enabled = pingEnabled,
                title = stringResource(R.string.restart_cooldown),
                leading = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                currentValue = uiState.monitoringSettings.restartCooldownSeconds,
                onSelected = { selected ->
                    selected?.let { viewModel.setRestartCooldownSeconds(it) }
                },
                options = listOf(5, 10, 15, 30, 60, 120, 300),
                optionToString = { it?.let { "${it}s" } ?: stringResource(R.string._default) },
            )
            SurfaceRow(
                enabled = pingEnabled,
                leading = {
                    Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null)
                },
                title = stringResource(R.string.exponential_backoff),
                description = {
                    Text(
                        text = stringResource(R.string.exponential_backoff_description),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline
                            ),
                    )
                },
                trailing = {
                    ThemedSwitch(
                        checked = uiState.monitoringSettings.isBackoffEnabled,
                        onClick = { viewModel.setBackoffEnabled(it) },
                        enabled = pingEnabled,
                    )
                },
                onClick = {
                    viewModel.setBackoffEnabled(!uiState.monitoringSettings.isBackoffEnabled)
                },
            )
            LabelledDropdown(
                enabled = pingEnabled,
                title = stringResource(R.string.max_restart_attempts),
                leading = { Icon(Icons.Outlined.Replay, contentDescription = null) },
                currentValue = uiState.monitoringSettings.maxRestartAttempts,
                onSelected = { selected -> selected?.let { viewModel.setMaxRestartAttempts(it) } },
                options =
                    if (uiState.monitoringSettings.isBackoffEnabled) listOf(3, 4, 5, 6, 7, 8)
                    else listOf(3, 5, 10, 20),
                optionToString = { n ->
                    if (n == null) return@LabelledDropdown stringResource(R.string._default)
                    val baseSec = uiState.monitoringSettings.restartCooldownSeconds.toLong()
                    val totalSec =
                        if (uiState.monitoringSettings.isBackoffEnabled) baseSec * ((1L shl n) - 1)
                        else baseSec * n
                    val display =
                        when {
                            totalSec < 60 -> "${totalSec}s"
                            totalSec < 3600 -> {
                                val m = totalSec / 60
                                val s = totalSec % 60
                                if (s == 0L) "${m}m" else "${m}m${s}s"
                            }
                            else -> {
                                val h = totalSec / 3600
                                val m = (totalSec % 3600) / 60
                                if (m == 0L) "${h}h" else "${h}h${m}m"
                            }
                        }
                    "$n attempts (~$display)"
                },
            )
            LabelledDropdown(
                enabled = pingEnabled,
                title = stringResource(R.string.max_attempts_action),
                leading = { Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null) },
                currentValue = uiState.monitoringSettings.maxAttemptsAction,
                onSelected = { selected -> selected?.let { viewModel.setMaxAttemptsAction(it) } },
                options = MaxAttemptsAction.entries.toList(),
                optionToString = { action ->
                    when (action) {
                        MaxAttemptsAction.DO_NOTHING ->
                            stringResource(R.string.max_attempts_action_do_nothing)
                        MaxAttemptsAction.STOP_TUNNEL ->
                            stringResource(R.string.max_attempts_action_stop_tunnel)
                        null -> stringResource(R.string._default)
                    }
                },
            )
        }

        val fallbackEnabled = uiState.monitoringSettings.isFallbackEnabled

        Column {
            GroupLabel(
                stringResource(R.string.enable_fallback_tunnel),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                enabled = pingEnabled,
                leading = { Icon(Icons.Outlined.AltRoute, contentDescription = null) },
                title = stringResource(R.string.enable_fallback_tunnel),
                description = {
                    Text(
                        text = stringResource(R.string.enable_fallback_tunnel_description),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline
                            ),
                    )
                },
                trailing = {
                    ThemedSwitch(
                        checked = fallbackEnabled,
                        onClick = { viewModel.setFallbackEnabled(it) },
                        enabled = pingEnabled,
                    )
                },
                onClick = { viewModel.setFallbackEnabled(!fallbackEnabled) },
            )
            LabelledDropdown(
                enabled = pingEnabled && fallbackEnabled,
                title = stringResource(R.string.default_fallback_tunnel),
                leading = { Icon(Icons.Outlined.AltRoute, contentDescription = null) },
                currentValue = uiState.monitoringSettings.defaultFallbackTunnelId,
                onSelected = { viewModel.setDefaultFallbackTunnelId(it) },
                options = uiState.tunnels.map { it.id } + listOf(null),
                optionToString = { id ->
                    if (id == null) stringResource(R.string.no_fallback)
                    else
                        uiState.tunnels.find { it.id == id }?.name
                            ?: stringResource(R.string.no_fallback)
                },
            )
            SurfaceRow(
                enabled = pingEnabled && fallbackEnabled,
                leading = { Icon(Icons.Outlined.AltRoute, contentDescription = null) },
                title = stringResource(R.string.per_tunnel_fallback),
                onClick = { navController.push(Route.FallbackTunnel) },
            )
        }
    }
}
