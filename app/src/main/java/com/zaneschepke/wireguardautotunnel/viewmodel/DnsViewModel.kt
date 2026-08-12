package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.dokar.sonner.ToastType
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.DnsSettingsCoordinator
import com.zaneschepke.wireguardautotunnel.domain.enums.BootstrapDnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsMode
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelDnsProtocol
import com.zaneschepke.wireguardautotunnel.domain.repository.DnsSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.DnsUiState
import com.zaneschepke.wireguardautotunnel.util.DnsValidator
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.labelRes
import kotlinx.coroutines.flow.combine
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

class DnsViewModel(
    private val dnsSettingsRepository: DnsSettingsRepository,
    private val tunnelRepository: TunnelRepository,
    private val networkMonitor: NetworkMonitor,
    private val globalEffectRepository: GlobalEffectRepository,
    private val dnsSettingsCoordinator: DnsSettingsCoordinator,
) : OrbitContainerHost<DnsUiState, DnsUiState, Nothing>, ViewModel() {

    override val container =
        orbitContainer<DnsUiState, Nothing>(
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
                            systemDnsInfo = connectivity?.underlyingDnsInfo,
                            isLoading = false,
                        )
                    } else {
                        state.copy(systemDnsInfo = connectivity?.underlyingDnsInfo)
                    }
                }
                .collect { newState -> reduce { newState } }
        }

    fun setBootstrapDnsProtocol(to: BootstrapDnsProtocol) = intent {
        reduce {
            state.copy(
                dnsSettings =
                    state.dnsSettings.copy(bootstrapDnsProtocol = to, bootstrapDnsEndpoint = null),
                bootstrapEndpointError = null,
            )
        }
    }

    fun setTunnelDnsProtocol(to: TunnelDnsProtocol) = intent {
        reduce {
            state.copy(
                dnsSettings = state.dnsSettings.copy(tunnelDnsProtocol = to),
                tunnelEndpointError = null,
            )
        }
    }

    fun save() = intent {
        val settings = state.dnsSettings

        when (
            val r =
                DnsValidator.validate(settings.bootstrapDnsProtocol, settings.bootstrapDnsEndpoint)
        ) {
            is DnsValidator.Result.Invalid -> {
                reduce { state.copy(bootstrapEndpointError = r.error) }
                postSideEffect(
                    GlobalSideEffect.Snackbar(
                        StringValue.StringResource(r.error.labelRes()),
                        type = ToastType.Error,
                    )
                )
                return@intent
            }
            DnsValidator.Result.Valid -> Unit
        }

        val usesTunnelDns =
            settings.tunnelDnsMode == TunnelDnsMode.Split &&
                settings.tunnelDnsProtocol == TunnelDnsProtocol.Plain &&
                settings.useTunnelDnsServersInSplit

        if (
            settings.tunnelDnsMode == TunnelDnsMode.Encrypted ||
                settings.tunnelDnsMode == TunnelDnsMode.Split
        ) {
            if (!usesTunnelDns) {
                when (
                    val r =
                        DnsValidator.validateTunnelEndpoint(
                            settings.tunnelDnsProtocol,
                            settings.tunnelDnsEndpoint,
                        )
                ) {
                    is DnsValidator.Result.Invalid -> {
                        reduce { state.copy(tunnelEndpointError = r.error) }
                        postSideEffect(
                            GlobalSideEffect.Snackbar(
                                StringValue.StringResource(r.error.labelRes()),
                                type = ToastType.Error,
                            )
                        )
                        return@intent
                    }
                    DnsValidator.Result.Valid -> Unit
                }
            }
        }

        if (settings.tunnelDnsMode == TunnelDnsMode.Split) {
            when (
                val r =
                    DnsValidator.validateLocalSuffixes(
                        settings.tunnelDnsMode,
                        settings.localSuffixes,
                    )
            ) {
                is DnsValidator.Result.Invalid -> {
                    reduce { state.copy(localSuffixesError = r.error) }
                    postSideEffect(
                        GlobalSideEffect.Snackbar(
                            StringValue.StringResource(r.error.labelRes()),
                            type = ToastType.Error,
                        )
                    )
                    return@intent
                }
                DnsValidator.Result.Valid -> Unit
            }
        }

        val updated =
            settings.copy(
                bootstrapDnsEndpoint =
                    DnsValidator.normalize(
                            settings.bootstrapDnsProtocol,
                            settings.bootstrapDnsEndpoint,
                        )
                        .ifEmpty { null },
                tunnelDnsEndpoint =
                    when (settings.tunnelDnsMode) {
                        TunnelDnsMode.Encrypted,
                        TunnelDnsMode.Split ->
                            if (!usesTunnelDns) {
                                DnsValidator.normalizeTunnelEndpoint(
                                    settings.tunnelDnsProtocol,
                                    settings.tunnelDnsEndpoint,
                                )
                            } else {
                                null
                            }
                        else -> null
                    },
                localSuffixes =
                    when (settings.tunnelDnsMode) {
                        TunnelDnsMode.Split ->
                            DnsValidator.normalizeLocalSuffixes(settings.localSuffixes).ifEmpty {
                                null
                            }
                        else -> null
                    },
            )

        dnsSettingsRepository.upsert(updated)
        dnsSettingsCoordinator.appyDnsSettings(updated)
        postSideEffect(GlobalSideEffect.PopBackStack)
        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.config_changes_saved),
                ToastType.Success,
            )
        )
    }

    fun setBootstrapDnsEndpoint(input: String) = intent {
        reduce {
            state.copy(
                dnsSettings = state.dnsSettings.copy(bootstrapDnsEndpoint = input),
                bootstrapEndpointError = null,
            )
        }
    }

    fun setLocalDomainSuffixes(input: String) = intent {
        reduce {
            state.copy(
                dnsSettings = state.dnsSettings.copy(localSuffixes = input),
                localSuffixesError = null,
            )
        }
    }

    fun setTunnelDnsMode(tunnelDnsMode: TunnelDnsMode) = intent {
        reduce {
            val protocol =
                if (
                    state.dnsSettings.tunnelDnsProtocol == TunnelDnsProtocol.Plain &&
                        tunnelDnsMode == TunnelDnsMode.Encrypted
                ) {
                    TunnelDnsProtocol.Doh
                } else {
                    state.dnsSettings.tunnelDnsProtocol
                }
            state.copy(
                dnsSettings =
                    state.dnsSettings.copy(
                        tunnelDnsMode = tunnelDnsMode,
                        tunnelDnsProtocol = protocol,
                    ),
                tunnelEndpointError = null,
                localSuffixesError = null,
            )
        }
    }

    fun setTunnelDnsEndpoint(input: String) = intent {
        reduce {
            state.copy(
                dnsSettings = state.dnsSettings.copy(tunnelDnsEndpoint = input),
                tunnelEndpointError = null,
            )
        }
    }

    fun setUseTunnelDnsServersInSplit(to: Boolean) = intent {
        reduce { state.copy(dnsSettings = state.dnsSettings.copy(useTunnelDnsServersInSplit = to)) }
    }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }
}
