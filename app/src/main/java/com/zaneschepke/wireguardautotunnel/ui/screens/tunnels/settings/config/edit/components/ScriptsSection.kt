package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox
import com.zaneschepke.wireguardautotunnel.ui.state.EditableInterface

@Composable
fun ScriptsSection(
    interfaceState: EditableInterface,
    onInterfaceChange: (EditableInterface) -> Unit,
) {
    val locale = Locale.current.platformLocale

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConfigurationTextBox(
            value = interfaceState.preUp,
            onValueChange = { onInterfaceChange(interfaceState.copy(preUp = it)) },
            label = stringResource(R.string.pre_up),
            hint =
                stringResource(
                    R.string.hint_template,
                    stringResource(R.string.comma_separated).lowercase(locale),
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.postUp,
            onValueChange = { onInterfaceChange(interfaceState.copy(postUp = it)) },
            label = stringResource(R.string.post_up),
            hint =
                stringResource(
                    R.string.hint_template,
                    stringResource(R.string.comma_separated).lowercase(locale),
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.preDown,
            onValueChange = { onInterfaceChange(interfaceState.copy(preDown = it)) },
            label = stringResource(R.string.pre_down),
            hint =
                stringResource(
                    R.string.hint_template,
                    stringResource(R.string.comma_separated).lowercase(locale),
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        ConfigurationTextBox(
            value = interfaceState.postDown,
            onValueChange = { onInterfaceChange(interfaceState.copy(postDown = it)) },
            label = stringResource(R.string.post_down),
            hint =
                stringResource(
                    R.string.hint_template,
                    stringResource(R.string.comma_separated).lowercase(locale),
                ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
