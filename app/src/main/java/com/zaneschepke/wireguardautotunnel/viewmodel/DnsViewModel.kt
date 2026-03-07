package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.data.model.DnsProtocol
import com.zaneschepke.wireguardautotunnel.data.model.DnsProvider
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.DnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.ui.state.DnsUiState
import kotlinx.coroutines.flow.combine
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class DnsViewModel(
    private val dnsSettingsRepository: DnsSettingsRepository,
    private val tunnelRepository: TunnelRepository,
) : ContainerHost<DnsUiState, Nothing>, ViewModel() {

    override val container =
        container<DnsUiState, Nothing>(
            DnsUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            combine(dnsSettingsRepository.flow, tunnelRepository.globalTunnelFlow) {
                    dnsSettings,
                    globalTunnel ->
                    state.copy(
                        dnsSettings = dnsSettings,
                        isLoading = false,
                        globalConfig = globalTunnel,
                    )
                }
                .collect { reduce { it } }
        }

    fun setDnsProtocol(to: DnsProtocol) = intent {
        dnsSettingsRepository.upsert(state.dnsSettings.copy(dnsProtocol = to))
    }

    fun setDnsProvider(dnsProvider: DnsProvider) = intent {
        dnsSettingsRepository.upsert(
            state.dnsSettings.copy(
                dnsEndpoint = dnsProvider.asAddress(state.dnsSettings.dnsProtocol)
            )
        )
    }

    fun setGlobalTunnelDnsEnabled(to: Boolean) = intent {
        dnsSettingsRepository.upsert(state.dnsSettings.copy(isGlobalTunnelDnsEnabled = to))
        if (state.globalConfig == null)
            tunnelRepository.save(TunnelConfig.generateDefaultGlobalConfig())
    }
}
