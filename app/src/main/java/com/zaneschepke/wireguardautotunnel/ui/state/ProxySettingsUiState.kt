package com.zaneschepke.wireguardautotunnel.ui.state

import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.domain.model.ProxySettings

data class ProxySettingsUiState(
    val proxySettings: ProxySettings = ProxySettings(),
    val backendStatus: BackendStatus = BackendStatus(),
    val isSocks5BindAddressError: Boolean = false,
    val isHttpBindAddressError: Boolean = false,
    val isUserNameError: Boolean = false,
    val isPasswordError: Boolean = false,
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = true,
    val showSaveModal: Boolean = false,
)
