package com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring.autorestart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingUp
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
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.SwitchWithDivider
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dropdown.LabelledDropdown
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.viewmodel.MonitoringViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun AutoRestartScreen(viewModel: MonitoringViewModel = koinViewModel()) {
    val uiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (uiState.isLoading) return

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
            SurfaceRow(
                enabled = uiState.monitoringSettings.isPingEnabled,
                leading = { Icon(Icons.Outlined.Adjust, contentDescription = null) },
                title = stringResource(R.string.use_ping_for_detection),
                trailing = {
                    ThemedSwitch(
                        checked = uiState.monitoringSettings.isPingMonitoringEnabled,
                        onClick = { viewModel.setPingMonitoringEnabled(it) },
                        enabled = uiState.monitoringSettings.isPingEnabled,
                    )
                },
                onClick = {
                    viewModel.setPingMonitoringEnabled(
                        !uiState.monitoringSettings.isPingMonitoringEnabled
                    )
                },
            )
            LabelledDropdown(
                title = stringResource(R.string.ping_failures_before_restart),
                leading = { Icon(Icons.Outlined.FilterAlt, contentDescription = null) },
                enabled = uiState.monitoringSettings.isPingEnabled && uiState.monitoringSettings.isPingMonitoringEnabled,
                currentValue = uiState.monitoringSettings.pingFailuresBeforeRestart,
                onSelected = { selected ->
                    selected?.let { viewModel.setPingFailuresBeforeRestart(it) }
                },
                options = listOf(1, 2, 3, 4, 5),
                optionToString = { it?.toString() ?: stringResource(R.string._default) },
            )
            LabelledDropdown(
                title = stringResource(R.string.restart_cooldown),
                leading = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                currentValue = uiState.monitoringSettings.restartCooldownSeconds,
                onSelected = { selected ->
                    selected?.let { viewModel.setRestartCooldownSeconds(it) }
                },
                options = listOf(3, 5, 10, 15, 30, 60, 120, 300),
                optionToString = { it?.let { "${it}s" } ?: stringResource(R.string._default) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.TrendingUp, contentDescription = null) },
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
                    )
                },
                onClick = {
                    viewModel.setBackoffEnabled(!uiState.monitoringSettings.isBackoffEnabled)
                },
            )
            LabelledDropdown(
                title = stringResource(R.string.backoff_timeout),
                leading = { Icon(Icons.Outlined.HourglassBottom, contentDescription = null) },
                enabled = uiState.monitoringSettings.isBackoffEnabled,
                currentValue = uiState.monitoringSettings.backoffMaxAttempts,
                onSelected = { selected ->
                    selected?.let { viewModel.setBackoffMaxAttempts(it) }
                },
                options = listOf(2, 3, 4, 5, 6, 7),
                optionToString = { n ->
                    if (n == null) return@LabelledDropdown stringResource(R.string._default)
                    val baseSec = uiState.monitoringSettings.restartCooldownSeconds.toLong()
                    val totalSec = baseSec * ((1L shl n) - 1)
                    val display = when {
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
                title = stringResource(R.string.max_handshake_restart_attempts),
                leading = { Icon(Icons.Outlined.Replay, contentDescription = null) },
                enabled = !uiState.monitoringSettings.isBackoffEnabled,
                description = {
                    Text(
                        text = stringResource(R.string.max_handshake_restart_attempts_description),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline
                            ),
                    )
                },
                currentValue = uiState.monitoringSettings.maxHandshakeRestartAttempts,
                onSelected = { selected ->
                    selected?.let { viewModel.setMaxHandshakeRestartAttempts(it) }
                },
                options = listOf(3, 5, 10, 20),
                optionToString = { it?.toString() ?: stringResource(R.string._default) },
            )
            LabelledDropdown(
                title = stringResource(R.string.max_attempts_action),
                leading = { Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null) },
                currentValue = uiState.monitoringSettings.maxAttemptsAction,
                onSelected = { selected -> selected?.let { viewModel.setMaxAttemptsAction(it) } },
                options = MaxAttemptsAction.entries.toList(),
                optionToString = { action ->
                    when (action) {
                        MaxAttemptsAction.DO_NOTHING -> stringResource(R.string.max_attempts_action_do_nothing)
                        MaxAttemptsAction.STOP_TUNNEL -> stringResource(R.string.max_attempts_action_stop_tunnel)
                        null -> stringResource(R.string._default)
                    }
                },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                title = stringResource(R.string.recovery_notifications),
                description = {
                    Text(
                        text = stringResource(R.string.recovery_notifications_description),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline
                            ),
                    )
                },
                trailing = { modifier ->
                    SwitchWithDivider(
                        checked = uiState.monitoringSettings.isRecoveryNotificationEnabled,
                        onClick = { viewModel.setRecoveryNotificationEnabled(it) },
                        modifier = modifier,
                    )
                },
            )
        }
    }
}
