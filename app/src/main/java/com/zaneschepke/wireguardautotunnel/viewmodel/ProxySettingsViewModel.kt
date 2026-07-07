package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.ProxySettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.ProxySettingsUiState
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.isValidAndroidProxyBindAddress
import kotlinx.coroutines.flow.combine
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class ProxySettingsViewModel(
    private val proxySettingsRepository: ProxySettingsRepository,
    private val globalEffectRepository: GlobalEffectRepository,
    private val tunnelCoordinator: TunnelCoordinator,
) : ContainerHost<ProxySettingsUiState, Nothing>, ViewModel() {

    override val container =
        container<ProxySettingsUiState, Nothing>(
            ProxySettingsUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            combine(tunnelCoordinator.backendStatus, proxySettingsRepository.flow) {
                    backendStatus,
                    settings ->
                    if (state.isLoading) {
                        ProxySettingsUiState(
                            proxySettings = settings,
                            backendStatus = backendStatus,
                            isLoading = false,
                            socks5Enabled = settings.socks5ProxyEnabled,
                            httpEnabled = settings.httpProxyEnabled,
                            socksBindAddress = settings.socks5ProxyBindAddress ?: "",
                            httpBindAddress = settings.httpProxyBindAddress ?: "",
                            proxyUsername = settings.proxyUsername ?: "",
                            proxyPassword = settings.proxyPassword ?: "",
                        )
                    } else {
                        state.copy(backendStatus = backendStatus)
                    }
                }
                .collect { reduce { it } }
        }

    // TODO add a dialog requesting restart if any tunnels active
    fun save() = intent {
        reduce { state.copy(showSaveModal = false) }

        val current = state

        val updated =
            current.proxySettings.copy(
                socks5ProxyEnabled = current.socks5Enabled,
                httpProxyEnabled = current.httpEnabled,
                socks5ProxyBindAddress = current.socksBindAddress.ifBlank { null },
                httpProxyBindAddress = current.httpBindAddress.ifBlank { null },
                proxyUsername = current.proxyUsername.ifBlank { null },
                proxyPassword = current.proxyPassword.ifBlank { null },
            )

        val isHttpDefault = updated.httpProxyBindAddress == null
        val isSocks5Default = updated.socks5ProxyBindAddress == null

        // Validate bind addresses
        if (!isSocks5Default && !updated.socks5ProxyBindAddress.isValidAndroidProxyBindAddress()) {
            return@intent reduce { state.copy(isSocks5BindAddressError = true) }
        }
        if (!isHttpDefault && !updated.httpProxyBindAddress.isValidAndroidProxyBindAddress()) {
            return@intent reduce { state.copy(isHttpBindAddressError = true) }
        }
        // Validate different ports
        if (!isHttpDefault && !isSocks5Default) {
            val socksPort = updated.socks5ProxyBindAddress.split(":").last().toIntOrNull()
            val httpPort = updated.httpProxyBindAddress.split(":").last().toIntOrNull()
            if (socksPort == null || httpPort == null || socksPort == httpPort) {
                return@intent postSideEffect(
                    GlobalSideEffect.Snackbar(
                        StringValue.StringResource(R.string.ports_must_differ),
                        ToastType.Error,
                    )
                )
            }
        }
        // Validate username and password (both null or both not null)
        if (!areBothNullOrBothNotNull(updated.proxyUsername, updated.proxyPassword)) {
            return@intent reduce {
                state.copy(
                    isUserNameError = updated.proxyUsername == null,
                    isPasswordError = updated.proxyPassword == null,
                )
            }
        }

        if (updated.proxyPassword?.any { it.isWhitespace() } == true) {
            postSideEffect(
                GlobalSideEffect.Snackbar(
                    StringValue.StringResource(R.string.password_no_spaces),
                    ToastType.Error,
                )
            )
            return@intent reduce { state.copy(isPasswordError = true) }
        }

        proxySettingsRepository.upsert(updated)

        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.config_changes_saved),
                ToastType.Success,
            )
        )
        postSideEffect(GlobalSideEffect.PopBackStack)
    }

    fun clearHttpBindError() = intent { reduce { state.copy(isHttpBindAddressError = false) } }

    fun clearSocks5BindError() = intent { reduce { state.copy(isSocks5BindAddressError = false) } }

    fun clearUsernameError() = intent { reduce { state.copy(isPasswordError = false) } }

    fun clearPasswordError() = intent { reduce { state.copy(isPasswordError = false) } }

    fun setShowSaveModal(showSaveModal: Boolean) = intent {
        reduce { state.copy(showSaveModal = showSaveModal) }
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    private fun areBothNullOrBothNotNull(s1: String?, s2: String?) = (s1 == null) == (s2 == null)

    fun onSocks5EnabledChanged(enabled: Boolean) = intent {
        reduce { state.copy(socks5Enabled = enabled) }
    }

    fun onHttpEnabledChanged(enabled: Boolean) = intent {
        reduce { state.copy(httpEnabled = enabled) }
    }

    fun onSocksBindChanged(value: String) = intent {
        reduce { state.copy(socksBindAddress = value, isSocks5BindAddressError = false) }
    }

    fun onHttpBindChanged(value: String) = intent {
        reduce { state.copy(httpBindAddress = value, isHttpBindAddressError = false) }
    }

    fun onUsernameChanged(value: String) = intent {
        reduce { state.copy(proxyUsername = value, isUserNameError = false) }
    }

    fun onPasswordChanged(value: String) = intent {
        reduce { state.copy(proxyPassword = value, isPasswordError = false) }
    }

    fun onPasswordVisibilityChanged(value: Boolean) = intent {
        reduce { state.copy(passwordVisible = value) }
    }
}
