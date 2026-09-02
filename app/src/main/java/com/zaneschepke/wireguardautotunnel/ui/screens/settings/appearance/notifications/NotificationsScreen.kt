package com.zaneschepke.wireguardautotunnel.ui.screens.settings.appearance.notifications

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.util.extensions.launchNotificationSettings
import com.zaneschepke.wireguardautotunnel.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun NotificationsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val context = LocalContext.current
    val settingsState by viewModel.collectAsState()

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxSize(),
    ) {
        Column {
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                title = stringResource(R.string.settings),
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                onClick = { context.launchNotificationSettings() },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && !settingsState.isLoading) {
                val settings = settingsState.settings
                SurfaceRow(
                    leading = {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                    },
                    title = stringResource(R.string.live_updates),
                    trailing = {
                        ThemedSwitch(
                            checked = settings.isLiveUpdatesEnabled,
                            onClick = { viewModel.setLiveUpdatesEnabled(it) },
                        )
                    },
                    description = { DescriptionText(stringResource(R.string.live_updates_desc)) },
                    onClick = { viewModel.setLiveUpdatesEnabled(!settings.isLiveUpdatesEnabled) },
                )
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.AltRoute, contentDescription = null) },
                    title = stringResource(R.string.notification_show_origin),
                    trailing = {
                        ThemedSwitch(
                            checked = settings.isNotificationOriginEnabled,
                            onClick = { viewModel.setNotificationOriginEnabled(it) },
                        )
                    },
                    description = {
                        DescriptionText(stringResource(R.string.notification_show_origin_desc))
                    },
                    onClick = {
                        viewModel.setNotificationOriginEnabled(
                            !settings.isNotificationOriginEnabled
                        )
                    },
                )
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.SwapVert, contentDescription = null) },
                    title = stringResource(R.string.notification_show_transfer),
                    trailing = {
                        ThemedSwitch(
                            checked = settings.isNotificationTransferEnabled,
                            onClick = { viewModel.setNotificationTransferEnabled(it) },
                        )
                    },
                    description = {
                        DescriptionText(stringResource(R.string.notification_show_transfer_desc))
                    },
                    onClick = {
                        viewModel.setNotificationTransferEnabled(
                            !settings.isNotificationTransferEnabled
                        )
                    },
                )
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.Replay, contentDescription = null) },
                    title = stringResource(R.string.notification_show_recovery),
                    trailing = {
                        ThemedSwitch(
                            checked = settings.isNotificationRecoveryEnabled,
                            onClick = { viewModel.setNotificationRecoveryEnabled(it) },
                        )
                    },
                    description = {
                        DescriptionText(stringResource(R.string.notification_show_recovery_desc))
                    },
                    onClick = {
                        viewModel.setNotificationRecoveryEnabled(
                            !settings.isNotificationRecoveryEnabled
                        )
                    },
                )
                SurfaceRow(
                    leading = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
                    title = stringResource(R.string.notification_failure_tint),
                    trailing = {
                        ThemedSwitch(
                            checked = settings.isNotificationFailureTintEnabled,
                            onClick = { viewModel.setNotificationFailureTintEnabled(it) },
                        )
                    },
                    description = {
                        DescriptionText(stringResource(R.string.notification_failure_tint_desc))
                    },
                    onClick = {
                        viewModel.setNotificationFailureTintEnabled(
                            !settings.isNotificationFailureTintEnabled
                        )
                    },
                )
            }
        }
    }
}
