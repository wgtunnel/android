package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R

@Composable
fun InterfaceDropdown(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showScripts: Boolean,
    showAmneziaValues: Boolean,
    isAmneziaCompatibilitySet: Boolean,
    onToggleScripts: () -> Unit,
    onToggleAmneziaValues: () -> Unit,
    onToggleAmneziaCompatibility: () -> Unit,
    onMimicQuic: () -> Unit,
    onMimicDns: () -> Unit,
    onMimicSip: () -> Unit,
) {
    Column {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.quick_actions),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.shadow(12.dp).background(MaterialTheme.colorScheme.surface),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (showScripts) stringResource(R.string.hide_scripts)
                        else stringResource(R.string.show_scripts)
                    )
                },
                onClick = {
                    onToggleScripts()
                    onExpandedChange(false)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (showAmneziaValues) stringResource(R.string.hide_amnezia_properties)
                        else stringResource(R.string.show_amnezia_properties)
                    )
                },
                onClick = {
                    onToggleAmneziaValues()
                    onExpandedChange(false)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (isAmneziaCompatibilitySet)
                            stringResource(R.string.remove_amnezia_compatibility)
                        else stringResource(R.string.enable_amnezia_compatibility)
                    )
                },
                onClick = {
                    onToggleAmneziaCompatibility()
                    onExpandedChange(false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mimic_quic)) },
                onClick = {
                    onMimicQuic()
                    onExpandedChange(false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mimic_dns)) },
                onClick = {
                    onMimicDns()
                    onExpandedChange(false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mimic_sip)) },
                onClick = {
                    onMimicSip()
                    onExpandedChange(false)
                },
            )
        }
    }
}
