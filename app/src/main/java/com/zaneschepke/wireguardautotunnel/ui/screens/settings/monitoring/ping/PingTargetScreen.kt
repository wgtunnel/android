package com.zaneschepke.wireguardautotunnel.ui.screens.settings.monitoring.ping

import android.util.Patterns
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Save
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.CustomTextField
import com.zaneschepke.wireguardautotunnel.util.extensions.isValidIpv4orIpv6Address
import com.zaneschepke.wireguardautotunnel.viewmodel.MonitoringViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun PingTargetScreen(viewModel: MonitoringViewModel = koinViewModel()) {

    val settingsState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (settingsState.isLoading) return

    var selectedTunnel by remember { mutableStateOf<TunnelConfig?>(null) }

    var isError by remember { mutableStateOf<Boolean>(false) }

    LazyColumn(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier.overscroll(rememberOverscrollEffect()),
        state = rememberLazyListState(),
        userScrollEnabled = true,
        reverseLayout = false,
        flingBehavior = ScrollableDefaults.flingBehavior(),
    ) {
        item {
            DescriptionText(
                stringResource(R.string.ping_target_description),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            )
            GroupLabel(
                stringResource(R.string.tunnels),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(settingsState.tunnels, key = { it.id }) { tunnel ->
            SurfaceRow(
                title = tunnel.name,
                onClick = { selectedTunnel = tunnel },
                description = {
                    DescriptionText(
                        stringResource(
                            R.string.ping_target_template,
                            tunnel.pingTarget ?: stringResource(R.string._default),
                        )
                    )
                },
                expandedContent = {
                    if (selectedTunnel?.id == tunnel.id) {
                        val focusRequester = remember { FocusRequester() }
                        val keyboardController = LocalSoftwareKeyboardController.current

                        var currentText by remember { mutableStateOf(tunnel.pingTarget ?: "") }
                        var isError by remember { mutableStateOf(false) }

                        LaunchedEffect(currentText) { isError = false }

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }

                        fun onClick() {
                            val isValid =
                                currentText.isBlank() ||
                                    currentText.isValidIpv4orIpv6Address() ||
                                    Patterns.DOMAIN_NAME.matcher(currentText).matches()
                            if (!isValid) {
                                isError = true
                                return
                            }
                            viewModel.setPingTarget(tunnel, currentText)
                            selectedTunnel = null
                        }

                        CustomTextField(
                            isError = isError,
                            textStyle =
                                MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                            value = currentText,
                            onValueChange = { currentText = it },
                            interactionSource = remember { MutableInteractionSource() },
                            label = {
                                Text(
                                    stringResource(R.string.set_ping_target),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            supportingText = {
                                DescriptionText(stringResource(R.string.ip_or_hostname))
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .padding(top = 8.dp),
                            singleLine = true,
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    imeAction = ImeAction.Done,
                                ),
                            keyboardActions = KeyboardActions(onDone = { onClick() }),
                            trailing = {
                                if (currentText != "") {
                                    IconButton(onClick = { onClick() }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Save,
                                            contentDescription = stringResource(R.string.save),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                        )
                    }
                },
            )
        }
    }
}
