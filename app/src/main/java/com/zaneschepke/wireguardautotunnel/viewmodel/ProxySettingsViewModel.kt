package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.domain.model.ProxySettings
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
    private val tunnelManager: TunnelManager,
) : ContainerHost<ProxySettingsUiState, Nothing>, ViewModel() {

    override val container =
        container<ProxySettingsUiState, Nothing>(
            ProxySettingsUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            combine(tunnelManager.activeTunnels, proxySettingsRepository.flow) {
                    activeTuns,
                    settings ->
                    state.copy(proxySettings = settings, isLoading = false, activeTuns = activeTuns)
                }
                .collect { reduce { it } }
        }

    fun save(proxySettings: ProxySettings) = intent {
        reduce { state.copy(showSaveModal = false) }
        val updated =
            state.proxySettings.copy(
                socks5ProxyEnabled = proxySettings.socks5ProxyEnabled,
                httpProxyEnabled = proxySettings.httpProxyEnabled,
                httpProxyBindAddress =
                    if (proxySettings.httpProxyEnabled) {
                        proxySettings.httpProxyBindAddress?.ifBlank { null }
                    } else {
                        null
                    },
                socks5ProxyBindAddress =
                    if (proxySettings.socks5ProxyEnabled) {
                        proxySettings.socks5ProxyBindAddress?.ifBlank { null }
                    } else {
                        null
                    },
                proxyUsername =
                    if (proxySettings.socks5ProxyEnabled || proxySettings.httpProxyEnabled) {
                        proxySettings.proxyUsername?.ifBlank { null }
                    } else {
                        null
                    },
                proxyPassword =
                    if (proxySettings.socks5ProxyEnabled || proxySettings.httpProxyEnabled) {
                        proxySettings.proxyPassword?.ifBlank { null }
                    } else {
                        null
                    },
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
                        StringValue.StringResource(R.string.ports_must_differ)
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
                GlobalSideEffect.Snackbar(StringValue.StringResource(R.string.password_no_spaces))
            )
            return@intent reduce { state.copy(isPasswordError = true) }
        }

        proxySettingsRepository.upsert(updated)

        if (state.activeTuns.isNotEmpty()) tunnelManager.restartActiveTunnels()

        postSideEffect(
            GlobalSideEffect.Snackbar(StringValue.StringResource(R.string.config_changes_saved))
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
}
