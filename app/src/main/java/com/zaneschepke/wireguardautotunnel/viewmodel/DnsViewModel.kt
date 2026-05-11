package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.data.model.DnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.DnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.parser.ConfigParseException
import com.zaneschepke.wireguardautotunnel.ui.state.DnsUiState
import com.zaneschepke.wireguardautotunnel.util.DnsValidator
import com.zaneschepke.wireguardautotunnel.util.StringValue
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

                        val tunnelDnsServers =
                            globalTunnelConfig?.getConfig()?.`interface`?.dns ?: ""

                        state.copy(
                            dnsSettings = dnsSettings,
                            globalTunnelConfig = globalTunnelConfig,
                            tunnelDnsServers = tunnelDnsServers,
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
        val updatedTunnelConfig =
            if (state.dnsSettings.isGlobalTunnelDnsEnabled) {
                val existingTunnelConfig =
                    state.globalTunnelConfig ?: TunnelConfig.generateDefaultGlobalConfig()

                val existingConfig = existingTunnelConfig.getConfig()

                val normalizedTunnelDns =
                    state.tunnelDnsServers
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(", ")
                        .takeIf { it.isNotEmpty() }

                val updatedConfig =
                    existingConfig.copy(
                        `interface` = existingConfig.`interface`.copy(dns = normalizedTunnelDns)
                    )

                try {
                    updatedConfig.validate()
                } catch (e: ConfigParseException) {
                    reduce { state.copy(dnsServersError = e.errorType) }
                    return@intent
                }

                existingTunnelConfig.copy(quickConfig = updatedConfig.asQuickString())
            } else null

        val protocol = state.dnsSettings.dnsProtocol
        val endpoint = state.dnsSettings.dnsEndpoint

        when (val result = DnsValidator.validate(protocol, endpoint)) {
            is DnsValidator.Result.Valid -> Unit
            is DnsValidator.Result.Invalid -> {
                reduce { state.copy(peerResolutionEndpointError = result.error) }
                return@intent
            }
        }

        val normalizedEndpoint = DnsValidator.normalize(protocol, endpoint)

        updatedTunnelConfig?.let { tunnelRepository.save(it) }

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

    fun setTunnelDnsServers(input: String) = intent {
        reduce { state.copy(tunnelDnsServers = input, dnsServersError = null) }
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun setGlobalTunnelDnsEnabled(to: Boolean) = intent {
        reduce {
            state.copy(
                dnsSettings = state.dnsSettings.copy(isGlobalTunnelDnsEnabled = to),
                dnsServersError = null,
            )
        }
    }
}
