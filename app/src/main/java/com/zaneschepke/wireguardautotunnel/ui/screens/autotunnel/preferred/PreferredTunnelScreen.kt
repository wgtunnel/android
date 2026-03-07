package com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.preferred

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.ui.LocalNavController
import com.zaneschepke.wireguardautotunnel.ui.common.button.ClickableIconButton
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.CustomTextField
import com.zaneschepke.wireguardautotunnel.ui.navigation.TunnelNetwork
import com.zaneschepke.wireguardautotunnel.ui.screens.autotunnel.components.WildcardsLabel
import com.zaneschepke.wireguardautotunnel.viewmodel.AutoTunnelViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun PreferredTunnelScreen(
    tunnelNetwork: TunnelNetwork,
    viewModel: AutoTunnelViewModel = koinViewModel(),
) {
    val navController = LocalNavController.current

    val autoTunnelState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (autoTunnelState.isLoading) return

    var selectedTunnel by remember { mutableStateOf<TunnelConfig?>(null) }

    val currentSelection =
        remember(autoTunnelState.tunnels) {
            when (tunnelNetwork) {
                TunnelNetwork.MOBILE_DATA ->
                    autoTunnelState.tunnels.firstOrNull { it.isMobileDataTunnel }
                TunnelNetwork.ETHERNET ->
                    autoTunnelState.tunnels.firstOrNull { it.isEthernetTunnel }
                TunnelNetwork.WIFI -> null
            }
        }

    LazyColumn(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
        modifier =
            Modifier.pointerInput(Unit) {
                    if (autoTunnelState.tunnels.isEmpty()) return@pointerInput
                }
                .overscroll(rememberOverscrollEffect()),
        state = rememberLazyListState(),
        userScrollEnabled = true,
        reverseLayout = false,
        flingBehavior = ScrollableDefaults.flingBehavior(),
    ) {
        item { GroupLabel(stringResource(R.string.tunnels), Modifier.padding(horizontal = 16.dp)) }
        if (tunnelNetwork != TunnelNetwork.WIFI) {
            item {
                SurfaceRow(
                    title =
                        buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(stringResource(R.string._default))
                            }
                        },
                    trailing =
                        if (currentSelection == null) {
                            {
                                Icon(
                                    Icons.Outlined.Check,
                                    stringResource(id = R.string.selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                    onClick = {
                        when (tunnelNetwork) {
                            TunnelNetwork.MOBILE_DATA -> {
                                viewModel.setPreferredMobileDataTunnel(null)
                                navController.pop()
                            }
                            TunnelNetwork.ETHERNET -> {
                                viewModel.setPreferredEthernetTunnel(null)
                                navController.pop()
                            }
                            TunnelNetwork.WIFI -> Unit
                        }
                    },
                )
            }
        }
        items(autoTunnelState.tunnels, key = { it.id }) { tunnel ->
            SurfaceRow(
                title = tunnel.name,
                trailing =
                    if (currentSelection?.id == tunnel.id) {
                        {
                            Icon(
                                Icons.Outlined.Check,
                                stringResource(id = R.string.selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                onClick = {
                    when (tunnelNetwork) {
                        TunnelNetwork.MOBILE_DATA -> {
                            viewModel.setPreferredMobileDataTunnel(tunnel)
                            navController.pop()
                        }

                        TunnelNetwork.ETHERNET -> {
                            viewModel.setPreferredEthernetTunnel(tunnel)
                            navController.pop()
                        }

                        TunnelNetwork.WIFI -> {
                            selectedTunnel = tunnel
                        }
                    }
                },
                expandedContent =
                    if (tunnelNetwork == TunnelNetwork.WIFI) {
                        {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                                ) {
                                    tunnel.tunnelNetworks.forEach { ssid ->
                                        ClickableIconButton(
                                            onClick = {
                                                viewModel.removeTunnelNetwork(tunnel, ssid)
                                            },
                                            text = ssid,
                                            icon = Icons.Filled.Close,
                                        )
                                    }
                                }
                                if (selectedTunnel?.id == tunnel.id) {
                                    val focusRequester = remember { FocusRequester() }
                                    val keyboardController = LocalSoftwareKeyboardController.current

                                    var currentText by remember { mutableStateOf("") }

                                    LaunchedEffect(Unit) {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }

                                    CustomTextField(
                                        textStyle =
                                            MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                        value = currentText,
                                        onValueChange = { currentText = it },
                                        interactionSource = remember { MutableInteractionSource() },
                                        label = {
                                            Text(
                                                stringResource(R.string.add_wifi_name),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        },
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        supportingText =
                                            if (
                                                autoTunnelState.autoTunnelSettings
                                                    .isWildcardsEnabled
                                            ) {
                                                { WildcardsLabel() }
                                            } else null,
                                        modifier =
                                            Modifier.fillMaxWidth().focusRequester(focusRequester),
                                        singleLine = true,
                                        keyboardOptions =
                                            KeyboardOptions(
                                                capitalization = KeyboardCapitalization.None,
                                                imeAction = ImeAction.Done,
                                            ),
                                        keyboardActions =
                                            KeyboardActions(
                                                onDone = {
                                                    viewModel.addTunnelNetwork(tunnel, currentText)
                                                    currentText = ""
                                                }
                                            ),
                                        trailing = {
                                            if (currentText != "") {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.addTunnelNetwork(
                                                            tunnel,
                                                            currentText,
                                                        )
                                                        currentText = ""
                                                    }
                                                ) {
                                                    val icon = Icons.Outlined.Add
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription =
                                                            stringResource(
                                                                R.string
                                                                    .trusted_ssid_value_description
                                                            ),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    } else null,
            )
        }
    }
}
