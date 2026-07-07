package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.MimicMode
import com.zaneschepke.wireguardautotunnel.parser.crypto.Key
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox
import com.zaneschepke.wireguardautotunnel.ui.state.ConfigUiState
import com.zaneschepke.wireguardautotunnel.ui.state.EditableInterface

@Composable
fun InterfaceSection(
    uiState: ConfigUiState,
    onInterfaceChange: (EditableInterface) -> Unit,
    onTunnelNameChange: (String) -> Unit,
    onMimic: (MimicMode) -> Unit,
    onToggleScripts: () -> Unit,
    onToggleAmneziaValues: () -> Unit,
    onToggleAmneziaCompat: () -> Unit,
    onToggleDropdown: (Boolean) -> Unit,
) {
    val isTv = LocalIsAndroidTV.current

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!uiState.isGlobalConfig || uiState.globalInterfaceSettingsEnabled) {
                    Box(
                        modifier = Modifier.height(48.dp).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        GroupLabel(stringResource(R.string.interface_))
                    }
                }
                if (!uiState.isGlobalConfig || uiState.showAmneziaGlobals)
                    Row {
                        if (isTv) {
                            if (!uiState.isGlobalConfig)
                                IconButton(
                                    enabled = true,
                                    onClick = {
                                        val privateKey = Key.generatePrivateKey()
                                        val publicKey = Key.generatePublicKey(privateKey)
                                        onInterfaceChange(
                                            uiState.draft.config.`interface`.copy(
                                                privateKey = privateKey.toBase64(),
                                                publicKey = publicKey.toBase64(),
                                            )
                                        )
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        stringResource(R.string.rotate_keys),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                        }
                        InterfaceDropdown(
                            uiState = uiState,
                            onToggleDropdown = onToggleDropdown,
                            onToggleScripts = onToggleScripts,
                            onToggleAmneziaValues = onToggleAmneziaValues,
                            onToggleAmneziaCompatibility = onToggleAmneziaCompat,
                            onMimic = onMimic,
                        )
                    }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                if (!uiState.isGlobalConfig)
                    ConfigurationTextBox(
                        value = uiState.draft.tunnelName,
                        enabled = !uiState.isRunning,
                        onValueChange = onTunnelNameChange,
                        label = stringResource(R.string.name),
                        isError = uiState.isTunnelNameTaken,
                        supportingText =
                            if (uiState.isRunning) {
                                {
                                    DescriptionText(
                                        stringResource(R.string.tunnel_running_name_message)
                                    )
                                }
                            } else null,
                        hint =
                            stringResource(
                                    R.string.hint_template,
                                    stringResource(R.string.tunnel_name),
                                )
                                .lowercase(locale = Locale.current.platformLocale),
                        modifier = Modifier.fillMaxWidth(),
                    )
                InterfaceFields(uiState = uiState, onInterfaceChange = onInterfaceChange)
            }
        }
    }
}
