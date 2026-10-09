package com.zaneschepke.wireguardautotunnel.ui.navigation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.navigation.NavController
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Addresses
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.AndroidIntegrations
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Appearance
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.AutoTunnel
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Config
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.ConfigEdit
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.ConfigGlobal
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Display
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Dns
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Donate
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.EntryTunnel
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.IPv6
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Language
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.License
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.LocationDisclosure
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Lock
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.LockdownSettings
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Logs
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Monitoring
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Notifications
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.PreferredTunnel
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.ProxySettings
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Security
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Settings
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.SplitTunnel
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.SplitTunnelGlobal
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Support
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.TunnelGlobals
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.TunnelRecovery
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.TunnelSettings
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.Tunnels
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.WifiDetectionMethod
import com.zaneschepke.wireguardautotunnel.ui.navigation.Route.WifiPreferences
import com.zaneschepke.wireguardautotunnel.ui.navigation.TunnelNetwork
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.GlobalAppUiState
import com.zaneschepke.wireguardautotunnel.ui.state.NavbarState
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel

@Composable
fun currentRouteAsNavbarState(
    globalState: GlobalAppUiState,
    sharedViewModel: SharedAppViewModel,
    route: Route?,
    navController: NavController,
): State<NavbarState> {
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    return remember(route, globalState) {
        derivedStateOf {
            when (route) {
                Appearance -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        showBottomItems = true,
                        topTitle = context.getString(R.string.appearance),
                    )
                }
                AutoTunnel -> {
                    NavbarState(
                        showBottomItems = true,
                        topTitle =
                            if (!globalState.isLocationDisclosureShown) null
                            else {
                                context.getString(R.string.auto_tunnel)
                            },
                    )
                }
                Display -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        showBottomItems = true,
                        topTitle = context.getString(R.string.display_theme),
                    )
                }
                Dns -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        showBottomItems = true,
                        topTitle = context.getString(R.string.dns_settings),
                        topTrailing = {
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    sharedViewModel.postSideEffect(LocalSideEffect.SaveChanges)
                                }
                            ) {
                                Icon(Icons.Rounded.Save, stringResource(R.string.save))
                            }
                        },
                    )
                }
                Language -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        showBottomItems = true,
                        topTitle = context.getString(R.string.language),
                    )
                }
                Notifications -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        showBottomItems = true,
                        topTitle = context.getString(R.string.notifications),
                    )
                }
                LockdownSettings -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        showBottomItems = true,
                        topTitle = context.getString(R.string.lockdown_settings),
                        topTrailing = {
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    sharedViewModel.postSideEffect(LocalSideEffect.SaveChanges)
                                }
                            ) {
                                Icon(Icons.Rounded.Save, stringResource(R.string.save))
                            }
                        },
                    )
                }
                License -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        showBottomItems = true,
                        topTitle = context.getString(R.string.licenses),
                    )
                }
                LocationDisclosure -> {
                    NavbarState(showBottomItems = true)
                }
                Lock -> {
                    NavbarState(showBottomItems = false)
                }
                Logs -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        showBottomItems = false,
                        topTitle = context.getString(R.string.logs),
                        topTrailing = {
                            IconButton(
                                onClick = {
                                    sharedViewModel.postSideEffect(
                                        LocalSideEffect.Sheet.LoggerActions
                                    )
                                }
                            ) {
                                Icon(Icons.Rounded.Menu, stringResource(R.string.quick_actions))
                            }
                        },
                    )
                }
                ProxySettings -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        showBottomItems = true,
                        topTitle = context.getString(R.string.proxy_settings),
                        topTrailing = {
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    sharedViewModel.postSideEffect(LocalSideEffect.SaveChanges)
                                }
                            ) {
                                Icon(Icons.Rounded.Save, stringResource(R.string.save))
                            }
                        },
                    )
                }
                Settings -> {
                    NavbarState(
                        showBottomItems = true,
                        topTitle = context.getString(R.string.settings),
                    )
                }
                is ConfigEdit,
                is ConfigGlobal -> {
                    val global = route !is ConfigEdit
                    val tunnelName =
                        if (!global) globalState.tunnelNames[route.id]
                        else context.getString(R.string.tunnel_configuration)
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        showBottomItems = true,
                        topTitle = tunnelName ?: context.getString(R.string.new_tunnel),
                        topTrailing = {
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    sharedViewModel.postSideEffect(LocalSideEffect.SaveChanges)
                                }
                            ) {
                                Icon(Icons.Rounded.Save, stringResource(R.string.save))
                            }
                            if (!global)
                                IconButton(
                                    onClick = {
                                        sharedViewModel.postSideEffect(
                                            LocalSideEffect.ShowSensitive
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.RemoveRedEye,
                                        stringResource(R.string.show_password),
                                    )
                                }
                            if (globalState.tunnelNames.isNotEmpty())
                                IconButton(
                                    onClick = {
                                        sharedViewModel.postSideEffect(
                                            LocalSideEffect.Modal.SelectTunnel
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentPasteGo,
                                        stringResource(R.string.copy_from),
                                    )
                                }
                        },
                    )
                }
                is SplitTunnel,
                is SplitTunnelGlobal -> {
                    val tunnelName =
                        if (route is SplitTunnel) globalState.tunnelNames[route.id]
                        else context.getString(R.string.splt_tunneling)
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = tunnelName ?: "",
                        topTrailing = {
                            Row {
                                IconButton(
                                    onClick = {
                                        sharedViewModel.postSideEffect(LocalSideEffect.SaveChanges)
                                    }
                                ) {
                                    Icon(Icons.Rounded.Save, stringResource(R.string.save))
                                }
                                IconButton(
                                    onClick = {
                                        sharedViewModel.postSideEffect(
                                            LocalSideEffect.Modal.SelectTunnel
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentPasteGo,
                                        stringResource(R.string.copy_from),
                                    )
                                }
                            }
                        },
                        showBottomItems = true,
                    )
                }
                Support -> {
                    NavbarState(
                        topTitle = context.getString(R.string.support),
                        showBottomItems = true,
                    )
                }
                AndroidIntegrations -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = context.getString(R.string.android_integrations),
                        showBottomItems = true,
                    )
                }
                is TunnelSettings -> {
                    val tunnelName = globalState.tunnelNames[route.id]
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        showBottomItems = true,
                        topTitle = tunnelName ?: "",
                    )
                }
                Tunnels -> {
                    val selecting = globalState.selectedTunnelCount > 0
                    NavbarState(
                        topLeading =
                            when {
                                globalState.isReorderMode -> {
                                    {
                                        TvBackButton {
                                            if (globalState.reorderScopeTitle != null) {
                                                sharedViewModel.exitReorderScope()
                                            } else {
                                                sharedViewModel.cancelReorder()
                                            }
                                        }
                                    }
                                }
                                selecting -> {
                                    {
                                        IconButton(
                                            onClick = sharedViewModel::clearSelectedTunnels
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                stringResource(R.string.cancel),
                                            )
                                        }
                                    }
                                }
                                else -> null
                            },
                        topTitle =
                            when {
                                globalState.isReorderMode &&
                                    globalState.reorderScopeTitle != null ->
                                    globalState.reorderScopeTitle
                                globalState.isReorderMode -> context.getString(R.string.reorder)
                                selecting ->
                                    context.getString(
                                        R.string.n_selected,
                                        globalState.selectedTunnelCount,
                                    )
                                else -> context.getString(R.string.tunnels)
                            },
                        topTrailing = {
                            when {
                                globalState.isReorderMode ->
                                    Row {
                                        IconButton(onClick = sharedViewModel::saveReorder) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                stringResource(R.string.done),
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                sharedViewModel.postSideEffect(
                                                    LocalSideEffect.Sheet.ReorderActions
                                                )
                                            }
                                        ) {
                                            Icon(
                                                Icons.Outlined.MoreVert,
                                                stringResource(R.string.more_options),
                                            )
                                        }
                                    }
                                selecting ->
                                    Row {
                                        IconButton(
                                            onClick = {
                                                sharedViewModel.postSideEffect(
                                                    LocalSideEffect.Sheet.SelectionActions
                                                )
                                            }
                                        ) {
                                            Icon(
                                                Icons.Outlined.MoreVert,
                                                stringResource(R.string.more_options),
                                            )
                                        }
                                    }
                                else ->
                                    Row {
                                        IconButton(
                                            onClick = {
                                                sharedViewModel.postSideEffect(
                                                    LocalSideEffect.Sheet.AddMenu
                                                )
                                            }
                                        ) {
                                            Icon(
                                                Icons.Rounded.Add,
                                                stringResource(R.string.add_tunnel),
                                            )
                                        }
                                        if (globalState.canReorder) {
                                            IconButton(
                                                onClick = sharedViewModel::enterReorderMode
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Reorder,
                                                    stringResource(R.string.reorder),
                                                )
                                            }
                                        }
                                    }
                            }
                        },
                        showBottomItems = true,
                    )
                }
                WifiDetectionMethod -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = context.getString(R.string.wifi_detection_method),
                        showBottomItems = true,
                    )
                }
                Donate -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = context.getString(R.string.donate_title),
                        showBottomItems = true,
                    )
                }
                Addresses -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = context.getString(R.string.addresses),
                        showBottomItems = true,
                    )
                }
                is WifiPreferences -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = context.getString(R.string.wifi_settings),
                        showBottomItems = true,
                    )
                }
                is PreferredTunnel -> {
                    val title =
                        when (route.tunnelNetwork) {
                            TunnelNetwork.MOBILE_DATA,
                            TunnelNetwork.ETHERNET -> context.getString(R.string.preferred_tunnel)
                            TunnelNetwork.WIFI -> context.getString(R.string.tunnel_mapping)
                        }
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = title,
                        showBottomItems = true,
                    )
                }
                is Config -> {
                    val tunnelName = globalState.tunnelNames[route.id] ?: ""
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTrailing = {
                            var showOverflowMenu by remember { mutableStateOf(false) }
                            if (!route.live) {
                                IconButton(onClick = { navController.push(ConfigEdit(route.id)) }) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.edit_tunnel),
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    sharedViewModel.postSideEffect(LocalSideEffect.ShowSensitive)
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.RemoveRedEye,
                                    stringResource(R.string.toggle_sensitive_data_visibility),
                                )
                            }

                            if (!route.live) {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        contentDescription = stringResource(R.string.more_options),
                                    )
                                }

                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    containerColor =
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    tonalElevation = 4.dp,
                                    shadowElevation = 4.dp,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.show_qr)) },
                                        leadingIcon = { Icon(Icons.Default.QrCode, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            sharedViewModel.postSideEffect(LocalSideEffect.Modal.QR)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.copy)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.ContentCopy,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            sharedViewModel.postSideEffect(
                                                LocalSideEffect.CopyToClipboard
                                            )
                                        },
                                    )
                                }
                            }
                        },
                        topTitle = tunnelName,
                        showBottomItems = true,
                    )
                }
                is IPv6 -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = context.getString(R.string.ipv6_settings),
                        showBottomItems = true,
                    )
                }
                is EntryTunnel -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = context.getString(R.string.entry_tunnel),
                        showBottomItems = true,
                    )
                }
                is TunnelGlobals -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = context.getString(R.string.tunnel_globals),
                        showBottomItems = true,
                    )
                }
                is Security -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = context.getString(R.string.security),
                        showBottomItems = true,
                    )
                }
                is Monitoring -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = context.getString(R.string.monitoring),
                        showBottomItems = true,
                    )
                }
                is TunnelRecovery -> {
                    NavbarState(
                        topLeading = { TvBackButton { navController.pop() } },
                        topTitle = context.getString(R.string.tunnel_recovery),
                        topTrailing = {
                            IconButton(
                                onClick = {
                                    sharedViewModel.postSideEffect(
                                        LocalSideEffect.Modal.RecoveryDetails
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.recovery_details),
                                )
                            }
                        },
                        showBottomItems = true,
                    )
                }
                null -> {
                    NavbarState()
                }
            }
        }
    }
}
