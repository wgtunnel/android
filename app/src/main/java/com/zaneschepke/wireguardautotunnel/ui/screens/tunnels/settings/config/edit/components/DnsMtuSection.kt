package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox
import com.zaneschepke.wireguardautotunnel.ui.state.EditableInterface

@Composable
fun DnsMtuSection(
    interfaceState: EditableInterface,
    onInterfaceChange: (EditableInterface) -> Unit,
    globalDnsServers: String? = null,
) {
    val locale = Locale.current.platformLocale

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConfigurationTextBox(
            value = interfaceState.dnsServers,
            onValueChange = { onInterfaceChange(interfaceState.copy(dnsServers = it)) },
            label = stringResource(R.string.dns_servers),
            hint =
                stringResource(R.string.hint_template, stringResource(R.string.comma_separated))
                    .lowercase(locale),
            supportingText = managedGloballySupporting(globalDnsServers),
            modifier = Modifier.fillMaxWidth(),
        )

        ConfigurationTextBox(
            value = interfaceState.mtu,
            onValueChange = { onInterfaceChange(interfaceState.copy(mtu = it)) },
            label = stringResource(R.string.mtu),
            hint = stringResource(R.string.auto).lowercase(locale),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
