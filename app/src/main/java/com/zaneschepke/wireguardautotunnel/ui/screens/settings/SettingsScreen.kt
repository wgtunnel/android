package com.zaneschepke.wireguardautotunnel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.MainActivity
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SheetButtonWithDivider
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.SwitchWithDivider
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.components.BackupBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.proxy.compoents.AppModeBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.theme.Disabled
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.asString
import com.zaneschepke.wireguardautotunnel.util.extensions.asTitleString
import com.zaneschepke.wireguardautotunnel.util.extensions.capitalize
import com.zaneschepke.wireguardautotunnel.util.extensions.showToast
import com.zaneschepke.wireguardautotunnel.viewmodel.SettingsViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val context = LocalContext.current
    val navController = LocalNavController.current

    val locale = Locale.current.platformLocale

    val globalUiState by sharedViewModel.container.stateFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (uiState.isLoading) return

    var showBackupSheet by rememberSaveable { mutableStateOf(false) }
    var showAppModeSheet by rememberSaveable { mutableStateOf(false) }

    val appMode = uiState.settings.appMode
    val dnsEnabled by rememberSaveable(appMode) { mutableStateOf(appMode != AppMode.KERNEL) }

    val showModeDivider by
        remember(appMode) {
            derivedStateOf { appMode == AppMode.PROXY || appMode == AppMode.LOCK_DOWN }
        }

    fun performBackupRestore(action: () -> Unit) {
        if (uiState.tunnelActive || globalUiState.isAutoTunnelActive)
            return context.showToast(R.string.all_services_disabled)
        showBackupSheet = false
        action()
    }

    if (showBackupSheet)
        BackupBottomSheet(
            { performBackupRestore { (context as? MainActivity)?.performBackup() } },
            { performBackupRestore { (context as? MainActivity)?.performRestore() } },
        ) {
            showBackupSheet = false
        }
    if (showAppModeSheet)
        AppModeBottomSheet(sharedViewModel::setAppMode, uiState.settings.appMode) {
            showAppModeSheet = false
        }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxSize(),
    ) {
        Column {
            GroupLabel(
                stringResource(R.string.tunnel).capitalize(locale),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = {
                    Icon(ImageVector.vectorResource(R.drawable.sdk), contentDescription = null)
                },
                trailing = { modifier ->
                    SheetButtonWithDivider(showModeDivider, modifier) { showAppModeSheet = true }
                },
                title = stringResource(R.string.backend_mode),
                description = {
                    DescriptionText(
                        stringResource(R.string.current_template, appMode.asTitleString(context))
                    )
                },
                onClick = {
                    when (appMode) {
                        AppMode.PROXY -> navController.push(Route.ProxySettings)
                        AppMode.LOCK_DOWN -> navController.push(Route.LockdownSettings)
                        AppMode.KERNEL,
                        AppMode.VPN -> showAppModeSheet = true
                    }
                },
            )
            SurfaceRow(
                leading = {
                    Icon(
                        Icons.Outlined.Dns,
                        null,
                        tint =
                            if (dnsEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outline,
                    )
                },
                title = stringResource(R.string.dns_settings),
                enabled = dnsEnabled,
                onClick = {
                    if (dnsEnabled) navController.push(Route.Dns)
                    else
                        sharedViewModel.showSnackMessage(
                            StringValue.StringResource(
                                R.string.mode_disabled_template,
                                appMode.asString(context),
                            )
                        )
                },
            )
            SurfaceRow(
                leading = {
                    Icon(
                        Icons.AutoMirrored.Outlined.CallSplit,
                        contentDescription = null,
                        tint =
                            if (globalUiState.appMode == AppMode.PROXY) Disabled
                            else MaterialTheme.colorScheme.onSurface,
                    )
                },
                enabled = globalUiState.appMode != AppMode.PROXY,
                title = stringResource(R.string.global_split_tunneling),
                trailing = { modifier ->
                    SwitchWithDivider(
                        checked = uiState.settings.isGlobalSplitTunnelEnabled,
                        onClick = { viewModel.setGlobalSplitTunneling(it) },
                        modifier = modifier,
                        enabled = globalUiState.appMode != AppMode.PROXY,
                    )
                },
                description =
                    if (globalUiState.appMode == AppMode.PROXY) {
                        {
                            DescriptionText(
                                stringResource(R.string.unavailable_in_mode),
                                disabled = true,
                            )
                        }
                    } else null,
                onClick = {
                    uiState.globalTunnelConfig?.let {
                        navController.push(Route.SplitTunnelGlobal(id = it.id))
                    }
                },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Android, null) },
                title = stringResource(R.string.android_integrations),
                onClick = { navController.push(Route.AndroidIntegrations) },
            )
        }
        Column {
            GroupLabel(
                stringResource(R.string.monitoring),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = {
                    Icon(
                        Icons.Outlined.NetworkPing,
                        contentDescription = null,
                        tint =
                            if (globalUiState.appMode != AppMode.PROXY)
                                MaterialTheme.colorScheme.onSurface
                            else Disabled,
                    )
                },
                title = stringResource(R.string.ping_monitor),
                enabled = globalUiState.appMode != AppMode.PROXY,
                description =
                    if (globalUiState.appMode == AppMode.PROXY) {
                        {
                            DescriptionText(
                                stringResource(R.string.unavailable_in_mode),
                                disabled = true,
                            )
                        }
                    } else null,
                trailing = { modifier ->
                    SwitchWithDivider(
                        checked = uiState.monitoring.isPingEnabled,
                        onClick = { viewModel.setPingEnabled(it) },
                        enabled = globalUiState.appMode != AppMode.PROXY,
                        modifier = modifier,
                    )
                },
                onClick = { navController.push(Route.TunnelMonitoring) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.ViewHeadline, contentDescription = null) },
                title = stringResource(R.string.local_logging),
                trailing = { modifier ->
                    SwitchWithDivider(
                        checked = uiState.monitoring.isLocalLogsEnabled,
                        onClick = { viewModel.setLocalLogging(it) },
                        modifier = modifier,
                    )
                },
                onClick = { navController.push(Route.Logs) },
            )
        }
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            GroupLabel(
                stringResource(R.string.general),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SurfaceRow(
                leading = {
                    Icon(Icons.AutoMirrored.Outlined.ViewQuilt, contentDescription = null)
                },
                title = stringResource(R.string.appearance),
                onClick = { navController.push(Route.Appearance) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Pin, contentDescription = null) },
                title = stringResource(R.string.enable_app_lock),
                trailing = {
                    ThemedSwitch(
                        checked = uiState.isPinLockEnabled,
                        onClick = {
                            if (it) {
                                navController.push(Route.Lock)
                            } else {
                                sharedViewModel.setPinLockEnabled(false)
                            }
                        },
                    )
                },
                onClick = {
                    if (!uiState.isPinLockEnabled) {
                        navController.push(Route.Lock)
                    } else {
                        sharedViewModel.setPinLockEnabled(false)
                    }
                },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.SettingsBackupRestore, contentDescription = null) },
                title = stringResource(R.string.backup_and_restore),
                onClick = { showBackupSheet = true },
                trailing = { modifier ->
                    IconButton(modifier = modifier, onClick = { showBackupSheet = true }) {
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(R.string.select),
                        )
                    }
                },
            )
        }
    }
}
