package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.MimicMode

@Composable
fun AmneziaToolMenuItems(
    compatibilitySet: Boolean,
    onToggleCompatibility: () -> Unit,
    onMimic: (MimicMode) -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                if (compatibilitySet) stringResource(R.string.remove_amnezia_compatibility)
                else stringResource(R.string.enable_amnezia_compatibility),
            )
        },
        onClick = onToggleCompatibility,
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.mimic_quic)) },
        onClick = { onMimic(MimicMode.QUIC) },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.mimic_dns)) },
        onClick = { onMimic(MimicMode.DNS) },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.mimic_sip)) },
        onClick = { onMimic(MimicMode.SIP) },
    )
}
