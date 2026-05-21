package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.DnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.repository.DnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.common.snackbar.SnackbarType
import com.zaneschepke.wireguardautotunnel.ui.state.DnsUiState
import com.zaneschepke.wireguardautotunnel.util.DnsValidator
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.labelRes
import kotlinx.coroutines.flow.combine
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class DnsViewModel(
    private val dnsSettingsRepository: DnsSettingsRepository,
    private val tunnelRepository: TunnelRepository,
    private val networkMonitor: NetworkMonitor,
    private val globalEffectRepository: GlobalEffectRepository,
) : ContainerHost<DnsUiState, Nothing>, ViewModel() {

    override val container =
        container<DnsUiState, Nothing>(
            DnsUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            combine(
                    dnsSettingsRepository.flow,
                    tunnelRepository.globalTunnelFlow,
                    networkMonitor.connectivityStateFlow,
                ) { dnsSettings, globalTunnelConfig, connectivity ->
                    if (state.isLoading) {
                        state.copy(
                            dnsSettings = dnsSettings,
                            globalTunnelConfig = globalTunnelConfig,
                            systemDnsInfo = connectivity.underlyingDnsInfo,
                            isLoading = false,
                        )
                    } else {
                        state.copy(systemDnsInfo = connectivity.underlyingDnsInfo)
                    }
                }
                .collect { newState -> reduce { newState } }
        }

    fun setDnsProtocol(to: DnsProtocol) = intent {
        reduce {
            state.copy(
                dnsSettings = state.dnsSettings.copy(dnsProtocol = to, dnsEndpoint = null),
                peerResolutionEndpointError = null,
            )
        }
    }

    fun save() = intent {
        val protocol = state.dnsSettings.dnsProtocol
        val endpoint = state.dnsSettings.dnsEndpoint

        when (val result = DnsValidator.validate(protocol, endpoint)) {
            is DnsValidator.Result.Valid -> Unit
            is DnsValidator.Result.Invalid -> {
                reduce { state.copy(peerResolutionEndpointError = result.error) }
                postSideEffect(
                    GlobalSideEffect.Snackbar(
                        StringValue.StringResource(result.error.labelRes()),
                        type = SnackbarType.WARNING,
                    )
                )
                return@intent
            }
        }

        val normalizedEndpoint = DnsValidator.normalize(protocol, endpoint)

        dnsSettingsRepository.upsert(
            state.dnsSettings.copy(dnsEndpoint = normalizedEndpoint, dnsProtocol = protocol)
        )

        postSideEffect(GlobalSideEffect.PopBackStack)
        postSideEffect(
            GlobalSideEffect.Toast(StringValue.StringResource(R.string.config_changes_saved))
        )
    }

    fun setDnsEndpoint(input: String) = intent {
        reduce {
            state.copy(
                dnsSettings = state.dnsSettings.copy(dnsEndpoint = input),
                peerResolutionEndpointError = null,
            )
        }
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }
}
