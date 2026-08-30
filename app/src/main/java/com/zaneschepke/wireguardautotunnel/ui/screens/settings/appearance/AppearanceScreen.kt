package com.zaneschepke.wireguardautotunnel.ui.screens.settings.appearance

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.util.extensions.launchNotificationSettings
import com.zaneschepke.wireguardautotunnel.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun AppearanceScreen(viewModel: SettingsViewModel = koinViewModel()) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize(),
    ) {
        val navController = LocalNavController.current
        val context = LocalContext.current
        val settingsState by viewModel.collectAsState()
        Column {
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Translate, contentDescription = null) },
                title = stringResource(R.string.language),
                onClick = { navController.push(Route.Language) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                title = stringResource(R.string.notifications),
                trailing = { Icon(Icons.AutoMirrored.Outlined.Launch, null) },
                onClick = { context.launchNotificationSettings() },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && !settingsState.isLoading) {
                val isLiveUpdatesEnabled = settingsState.settings.isLiveUpdatesEnabled
                SurfaceRow(
                    leading = {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                    },
                    title = stringResource(R.string.live_updates),
                    trailing = {
                        ThemedSwitch(
                            checked = isLiveUpdatesEnabled,
                            onClick = { viewModel.setLiveUpdatesEnabled(it) },
                        )
                    },
                    description = { DescriptionText(stringResource(R.string.live_updates_desc)) },
                    onClick = { viewModel.setLiveUpdatesEnabled(!isLiveUpdatesEnabled) },
                )
            }
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Contrast, contentDescription = null) },
                title = stringResource(R.string.display_theme),
                onClick = { navController.push(Route.Display) },
            )
        }
    }
}
