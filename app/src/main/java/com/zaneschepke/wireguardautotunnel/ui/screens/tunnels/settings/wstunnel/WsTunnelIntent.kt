package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.settings.wstunnel

sealed class WsTunnelIntent {
    data class ToggleEnabled(val value: Boolean) : WsTunnelIntent()

    data class UpdateServerUrl(val value: String) : WsTunnelIntent()

    data class UpdatePathPrefix(val value: String) : WsTunnelIntent()

    data class UpdateSniOverride(val value: String) : WsTunnelIntent()
}
