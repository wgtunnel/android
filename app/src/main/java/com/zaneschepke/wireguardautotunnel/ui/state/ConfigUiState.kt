package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.wireguardautotunnel.data.entity.TunnelConfig as Entity
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig

data class ConfigUiState(
    val isLoading: Boolean = true,
    val initialized: Boolean = false,

    // persisted/backend state
    val tunnel: TunnelConfig? = null,
    val tunnels: List<TunnelSummary> = emptyList(),
    val isRunning: Boolean = false,

    // editable draft
    val draft: ConfigDraft = ConfigDraft(),

    // persisted settings
    val globalSettings: GlobalSettingsState = GlobalSettingsState(),

    // ui
    val ui: ConfigEditUiState = ConfigEditUiState(),
) {
    val isGlobalConfig: Boolean
        get() = tunnel?.name == Entity.GLOBAL_CONFIG_NAME

    val showAmneziaGlobals: Boolean
        get() = isGlobalConfig && globalSettings.amneziaEnabled

    val globalInterfaceSettingsEnabled: Boolean
        get() = globalSettings.dnsEnabled || globalSettings.amneziaEnabled

    val isAmneziaCompatibilitySet: Boolean
        get() = draft.config.`interface`.isAmneziaCompatibilityModeSet()

    val isTunnelNameTaken: Boolean
        get() = tunnels.any { it.id != tunnel?.id && it.name == draft.tunnelName }

    val isDirty: Boolean
        get() {
            val original = tunnel ?: return false

            return draft !=
                ConfigDraft(
                    tunnelName = original.name,
                    config = EditableConfig.from(original.getConfig()),
                )
        }
}

data class ConfigEditUiState(
    val showScripts: Boolean = false,
    val showSensitiveData: Boolean = false,
    val showAmneziaValues: Boolean = false,
    val isInterfaceDropdownExpanded: Boolean = false,
    val isPeerDropdownExpanded: Boolean = false,
    val showSaveModal: Boolean = false,
    val selectedCopySourceTunnelId: Int? = null,
)

data class GlobalSettingsState(val dnsEnabled: Boolean = false, val amneziaEnabled: Boolean = false)

data class ConfigDraft(val tunnelName: String = "", val config: EditableConfig = EditableConfig())

data class TunnelSummary(val id: Int, val name: String)
