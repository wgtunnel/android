package com.zaneschepke.wireguardautotunnel.ui.screens.settings.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun SecurityScreen(viewModel: SharedAppViewModel = koinActivityViewModel()) {

    val uiState by viewModel.collectAsState()

    val navController = LocalNavController.current

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxSize(),
    ) {
        SurfaceRow(
            leading = { Icon(Icons.Outlined.ScreenLockPortrait, contentDescription = null) },
            title = stringResource(R.string.screen_recording_protection),
            trailing = {
                ThemedSwitch(
                    checked = uiState.isScreenRecordingProtectionEnabled,
                    onClick = { viewModel.setScreenRecordingSecurity(it) },
                )
            },
            onClick = {
                viewModel.setScreenRecordingSecurity(!uiState.isScreenRecordingProtectionEnabled)
            },
        )
        SurfaceRow(
            leading = { Icon(Icons.Outlined.Pin, contentDescription = null) },
            title = stringResource(R.string.enable_app_lock),
            trailing = {
                ThemedSwitch(
                    checked = uiState.pinLockEnabled,
                    onClick = {
                        if (it) {
                            navController.push(Route.Lock)
                        } else {
                            viewModel.setPinLockEnabled(false)
                        }
                    },
                )
            },
            onClick = {
                if (!uiState.pinLockEnabled) {
                    navController.push(Route.Lock)
                } else {
                    viewModel.setPinLockEnabled(false)
                }
            },
        )
    }
}
