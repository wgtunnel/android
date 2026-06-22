package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.config.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.ui.state.ConfigUiState
import com.zaneschepke.wireguardautotunnel.ui.state.EditableInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceFields(uiState: ConfigUiState, onInterfaceChange: (EditableInterface) -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
    val keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!uiState.isGlobalConfig) {
            BasicInterfaceFields(
                interfaceState = uiState.draft.config.`interface`,
                onInterfaceChange = onInterfaceChange,
                showPrivateKey = uiState.ui.showSensitiveData,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
            )
        }

        DnsMtuSection(
            isGlobalConfig = uiState.isGlobalConfig,
            globalDnsEnabled = uiState.globalSettings.dnsEnabled,
            interfaceState = uiState.draft.config.`interface`,
            onInterfaceChange = onInterfaceChange,
        )

        if (uiState.ui.showScripts) {
            ScriptsSection(uiState.draft.config.`interface`, onInterfaceChange)
        }

        if (
            (!uiState.isGlobalConfig && uiState.ui.showAmneziaValues) ||
                (uiState.isGlobalConfig && uiState.globalSettings.amneziaEnabled)
        ) {
            AmneziaSection(uiState.draft.config.`interface`, onInterfaceChange)
        }
    }
}
