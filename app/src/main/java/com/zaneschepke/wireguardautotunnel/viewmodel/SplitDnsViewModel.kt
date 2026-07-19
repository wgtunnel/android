package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.lifecycle.ViewModel
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.dokar.sonner.ToastType
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.SplitDnsUiState
import com.zaneschepke.wireguardautotunnel.util.DnsValidator
import com.zaneschepke.wireguardautotunnel.util.StringValue
import com.zaneschepke.wireguardautotunnel.util.extensions.labelRes
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

class SplitDnsViewModel(
    private val tunnelRepository: TunnelRepository,
    private val globalEffectRepository: GlobalEffectRepository,
    val tunnelId: Int,
) : ContainerHost<SplitDnsUiState, Nothing>, ViewModel() {

    override val container =
        container<SplitDnsUiState, Nothing>(
            SplitDnsUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            tunnelRepository.flow
                .map { list -> list.firstOrNull { it.id == tunnelId } }
                .collect { tunnel ->
                    reduce {
                        val initialized = state.tunnel != null
                        state.copy(
                            tunnel = tunnel,
                            isLoading = false,
                            tunnelHasDnsServer = tunnel?.hasDnsServer() ?: false,
                            // Preserve in-progress edits once initialized.
                            domains =
                                if (initialized) state.domains
                                else tunnel?.splitDnsDomains?.sorted().orEmpty(),
                        )
                    }
                }
        }

    /** Clears any input error as the user edits; the field text itself is owned by the screen. */
    fun onInputChanged() = intent {
        if (state.inputError != null) reduce { state.copy(inputError = null) }
    }

    fun addDomain(rawInput: String) = intent {
        when (val result = DnsValidator.validateDomain(rawInput)) {
            is DnsValidator.Result.Valid -> Unit
            is DnsValidator.Result.Invalid -> {
                reduce { state.copy(inputError = result.error) }
                postSideEffect(
                    GlobalSideEffect.Snackbar(
                        StringValue.StringResource(result.error.labelRes()),
                        type = ToastType.Warning,
                    )
                )
                return@intent
            }
        }

        val normalized = DnsValidator.normalizeDomain(rawInput)
        reduce {
            if (state.domains.contains(normalized)) state
            else state.copy(domains = (state.domains + normalized).sorted())
        }
    }

    fun removeDomain(domain: String) = intent {
        reduce { state.copy(domains = state.domains - domain) }
    }

    fun save() = intent {
        val tunnel = state.tunnel ?: return@intent
        tunnelRepository.save(tunnel.copy(splitDnsDomains = state.domains.toSet()))
        postSideEffect(
            GlobalSideEffect.Snackbar(
                StringValue.StringResource(R.string.config_changes_saved),
                type = ToastType.Success,
            )
        )
        postSideEffect(GlobalSideEffect.PopBackStack)
    }

    private suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }
}

private fun TunnelConfig.hasDnsServer(): Boolean =
    runCatching { getConfig().`interface`.dns?.isNotBlank() == true }.getOrDefault(false)
