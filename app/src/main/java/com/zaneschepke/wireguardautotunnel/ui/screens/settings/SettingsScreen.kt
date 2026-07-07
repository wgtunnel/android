package com.zaneschepke.wireguardautotunnel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CellWifi
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.MainActivity
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelMode
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.SheetButtonWithDivider
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.SwitchWithDivider
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.components.BackupBottomSheet
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.components.BackupEncryptionDialog
import com.zaneschepke.wireguardautotunnel.ui.screens.settings.proxy.compoents.AppModeBottomSheet
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.asString
import com.zaneschepke.wireguardautotunnel.util.extensions.asTitleString
import com.zaneschepke.wireguardautotunnel.util.extensions.capitalize
import com.zaneschepke.wireguardautotunnel.viewmodel.SettingsViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val isTv = LocalIsAndroidTV.current

    val locale = Locale.current.platformLocale

    val globalUiState by sharedViewModel.collectAsState()
    val uiState by viewModel.collectAsState()

    if (uiState.isLoading) return

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTv) {
            focusRequester.requestFocus()
        }
    }

    var showBackupSheet by rememberSaveable { mutableStateOf(false) }
    var showEncryptionDialog by rememberSaveable { mutableStateOf(false) }
    var isRestoreAction by remember { mutableStateOf(false) }

    var showAppModeSheet by rememberSaveable { mutableStateOf(false) }

    val appMode = uiState.settings.tunnelMode
    val dnsEnabled by rememberSaveable(appMode) { mutableStateOf(true) }

    val showModeDivider by
        remember(appMode) {
            derivedStateOf { appMode == TunnelMode.PROXY || appMode == TunnelMode.LOCK_DOWN }
        }

    fun performBackupRestore(action: () -> Unit) {
        showBackupSheet = false
        if (uiState.tunnelActive || globalUiState.isAutoTunnelActive) {
            sharedViewModel.showSnackMessage(
                StringValue.StringResource(R.string.all_services_disabled),
                ToastType.Warning,
            )
            return
        }
        action()
    }

    if (showBackupSheet) {
        BackupBottomSheet(
            onBackup = {
                showBackupSheet = false
                isRestoreAction = false
                showEncryptionDialog = true
            },
            onRestore = {
                showBackupSheet = false
                isRestoreAction = true
                showEncryptionDialog = true
            },
            onDismiss = { showBackupSheet = false },
        )
    }

    if (showEncryptionDialog) {
        BackupEncryptionDialog(
            isRestore = isRestoreAction,
            onConfirm = { encrypt, password ->
                showEncryptionDialog = false

                if (isRestoreAction) {
                    performBackupRestore {
                        (context as? MainActivity)?.performRestore(encrypt, password)
                    }
                } else {
                    performBackupRestore {
                        (context as? MainActivity)?.performBackup(encrypt, password)
                    }
                }
            },
            onDismiss = { showEncryptionDialog = false },
        )
    }

    if (showAppModeSheet)
        AppModeBottomSheet(sharedViewModel::setAppMode, uiState.settings.tunnelMode) {
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
                        TunnelMode.PROXY -> navController.push(Route.ProxySettings)
                        TunnelMode.LOCK_DOWN -> navController.push(Route.LockdownSettings)
                        TunnelMode.VPN -> showAppModeSheet = true
                    }
                },
                modifier = Modifier.focusRequester(focusRequester),
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
                            ),
                            ToastType.Info,
                        )
                },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Public, contentDescription = null) },
                title = stringResource(R.string.tunnel_globals),
                onClick = { navController.push(Route.TunnelGlobals) },
                description = { DescriptionText(stringResource(R.string.tunnel_globals_desc)) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                title = stringResource(R.string.tunnel_scripting),
                trailing = { modifier ->
                    ThemedSwitch(
                        checked = uiState.settings.tunnelScriptingEnabled,
                        onClick = { viewModel.setTunnelScriptedEnabled(it) },
                        modifier = modifier,
                    )
                },
                description = {
                    DescriptionText(stringResource(R.string.root_required_template, "").trim())
                },
                onClick = {
                    viewModel.setTunnelScriptedEnabled(!uiState.settings.tunnelScriptingEnabled)
                },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.CellWifi, contentDescription = null) },
                title = stringResource(R.string.seamless_roaming),
                trailing = { modifier ->
                    ThemedSwitch(
                        checked = uiState.settings.seamlessRoamingEnabled,
                        onClick = { viewModel.setSeamlessNetworkRoaming(enabled = it) },
                        modifier = modifier,
                    )
                },
                description = {
                    DescriptionText(stringResource(R.string.seamless_roaming_description))
                },
                onClick = {
                    viewModel.setSeamlessNetworkRoaming(!uiState.settings.seamlessRoamingEnabled)
                },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.MonitorHeart, null) },
                title = stringResource(R.string.tunnel_monitoring),
                onClick = { navController.push(Route.Monitoring) },
            )
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Android, null) },
                title = stringResource(R.string.android_integrations),
                onClick = { navController.push(Route.AndroidIntegrations) },
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
                leading = { Icon(Icons.Outlined.Security, contentDescription = null) },
                title = stringResource(R.string.security),
                onClick = { navController.push(Route.Security) },
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
                description = { DescriptionText(stringResource(R.string.local_logging_desc)) },
                onClick = { navController.push(Route.Logs) },
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
