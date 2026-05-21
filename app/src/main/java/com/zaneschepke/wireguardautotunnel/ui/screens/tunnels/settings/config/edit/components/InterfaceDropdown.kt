package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.MimicMode
import com.zaneschepke.wireguardautotunnel.ui.state.ConfigUiState

@Composable
fun InterfaceDropdown(
    uiState: ConfigUiState,
    onToggleDropdown: (Boolean) -> Unit,
    onToggleScripts: () -> Unit,
    onToggleAmneziaValues: () -> Unit,
    onToggleAmneziaCompatibility: () -> Unit,
    onMimic: (MimicMode) -> Unit,
) {
    Column {
        IconButton(onClick = { onToggleDropdown(true) }) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.quick_actions),
            )
        }
        DropdownMenu(
            expanded = uiState.ui.isInterfaceDropdownExpanded,
            onDismissRequest = { onToggleDropdown(false) },
            modifier = Modifier.shadow(12.dp).background(MaterialTheme.colorScheme.surface),
        ) {
            if (!uiState.isGlobalConfig)
                DropdownMenuItem(
                    text = {
                        Text(
                            if (uiState.ui.showScripts) stringResource(R.string.hide_scripts)
                            else stringResource(R.string.show_scripts)
                        )
                    },
                    onClick = {
                        onToggleScripts()
                        onToggleDropdown(false)
                    },
                )
            if (!uiState.isGlobalConfig)
                DropdownMenuItem(
                    text = {
                        Text(
                            if (uiState.ui.showAmneziaValues)
                                stringResource(R.string.hide_amnezia_properties)
                            else stringResource(R.string.show_amnezia_properties)
                        )
                    },
                    onClick = {
                        onToggleAmneziaValues()
                        onToggleDropdown(false)
                    },
                )
            DropdownMenuItem(
                text = {
                    Text(
                        if (uiState.isAmneziaCompatibilitySet)
                            stringResource(R.string.remove_amnezia_compatibility)
                        else stringResource(R.string.enable_amnezia_compatibility)
                    )
                },
                onClick = {
                    onToggleAmneziaCompatibility()
                    onToggleDropdown(false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mimic_quic)) },
                onClick = {
                    onMimic(MimicMode.QUIC)
                    onToggleDropdown(false)
                },
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.mimic_dns)) },
                onClick = {
                    onMimic(MimicMode.DNS)
                    onToggleDropdown(false)
                },
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.mimic_sip)) },
                onClick = {
                    onMimic(MimicMode.SIP)
                    onToggleDropdown(false)
                },
            )
        }
    }
}
