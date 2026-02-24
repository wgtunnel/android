package com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring.autorestart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
            LabelledDropdown(
                title = stringResource(R.string.restart_cooldown),
                leading = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                currentValue = uiState.monitoringSettings.restartCooldownSeconds,
                onSelected = { selected ->
                    selected?.let { viewModel.setRestartCooldownSeconds(it) }
                },
                options = listOf(15, 30, 60, 120, 300),
                optionToString = { it?.let { "${it}s" } ?: stringResource(R.string._default) },
            )
            LabelledDropdown(
                title = stringResource(R.string.max_handshake_restart_attempts),
                leading = { Icon(Icons.Outlined.Replay, contentDescription = null) },
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
        }
    }
}
