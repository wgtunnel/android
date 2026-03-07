package com.zaneschepke.wireguardautotunnel.viewmodel

import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.lifecycle.ViewModel
import com.zaneschepke.networkmonitor.NetworkMonitor
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.service.ServiceManager
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.data.model.WifiDetectionMethod
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.repository.AutoTunnelSettingsRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.GlobalEffectRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.domain.sideeffect.GlobalSideEffect
import com.zaneschepke.wireguardautotunnel.ui.state.AutoTunnelUiState
import com.zaneschepke.wireguardautotunnel.util.RootShellUtils
import com.zaneschepke.wireguardautotunnel.util.StringValue
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import rikka.shizuku.Shizuku

class AutoTunnelViewModel(
    private val autoTunnelRepository: AutoTunnelSettingsRepository,
    private val serviceManager: ServiceManager,
    private val networkMonitor: NetworkMonitor,
    private val globalEffectRepository: GlobalEffectRepository,
    private val tunnelsRepository: TunnelRepository,
    private val rootShellUtils: RootShellUtils,
) : ContainerHost<AutoTunnelUiState, Nothing>, ViewModel() {

    init {
        networkMonitor.checkPermissionsAndUpdateState()
    }

    override val container =
        container<AutoTunnelUiState, Nothing>(
            AutoTunnelUiState(),
            buildSettings = { repeatOnSubscribedStopTimeout = 5000L },
        ) {
            intent {
                combine(
                        networkMonitor.connectivityStateFlow,
                        serviceManager.autoTunnelService.map { it != null },
                        autoTunnelRepository.flow,
                        tunnelsRepository.userTunnelsFlow,
                    ) { connectivity, active, autoTunnel, tunnels ->
                        state.copy(
                            autoTunnelActive = active,
                            connectivityState = connectivity,
                            autoTunnelSettings = autoTunnel,
                            tunnels = tunnels,
                            isLoading = false,
                        )
                    }
                    .collect { reduce { it } }
            }
        }

    suspend fun postSideEffect(globalSideEffect: GlobalSideEffect) {
        globalEffectRepository.post(globalSideEffect)
    }

    fun toggleAutoTunnel(appMode: AppMode) = intent {
        if (!state.autoTunnelActive) {
            when (appMode) {
                AppMode.VPN ->
                    if (!serviceManager.hasVpnPermission())
                        return@intent postSideEffect(
                            GlobalSideEffect.RequestVpnPermission(AppMode.VPN, null)
                        )
                else -> Unit
            }
            autoTunnelRepository.upsert(state.autoTunnelSettings.copy(isAutoTunnelEnabled = true))
        } else {
            autoTunnelRepository.upsert(state.autoTunnelSettings.copy(isAutoTunnelEnabled = false))
        }
    }

    fun setAutoTunnelOnWifiEnabled(to: Boolean) = intent {
        autoTunnelRepository.upsert(state.autoTunnelSettings.copy(isTunnelOnWifiEnabled = to))
    }

    fun setWildcardsEnabled(to: Boolean) = intent {
        autoTunnelRepository.upsert(state.autoTunnelSettings.copy(isWildcardsEnabled = to))
    }

    fun setStopOnNoInternetEnabled(to: Boolean) = intent {
        autoTunnelRepository.upsert(state.autoTunnelSettings.copy(isStopOnNoInternetEnabled = to))
    }

    fun saveTrustedNetworkName(name: String) = intent {
        if (name.isEmpty()) return@intent
        val trimmed = name.trim()
        if (state.autoTunnelSettings.trustedNetworkSSIDs.contains(name)) {
            return@intent postSideEffect(
                GlobalSideEffect.Snackbar(StringValue.StringResource(R.string.error_ssid_exists))
            )
        }
        setTrustedNetworkNames(
            (state.autoTunnelSettings.trustedNetworkSSIDs + trimmed).toMutableSet()
        )
    }

    fun setTrustedNetworkNames(to: Set<String>) = intent {
        autoTunnelRepository.upsert(state.autoTunnelSettings.copy(trustedNetworkSSIDs = to))
    }

    fun removeTrustedNetworkName(name: String) = intent {
        setTrustedNetworkNames((state.autoTunnelSettings.trustedNetworkSSIDs - name).toMutableSet())
    }

    fun setTunnelOnCellular(to: Boolean) = intent {
        autoTunnelRepository.upsert(state.autoTunnelSettings.copy(isTunnelOnMobileDataEnabled = to))
    }

    fun setTunnelOnEthernet(to: Boolean) = intent {
        autoTunnelRepository.upsert(state.autoTunnelSettings.copy(isTunnelOnEthernetEnabled = to))
    }

    fun setStartAtBoot(to: Boolean) = intent {
        autoTunnelRepository.upsert(state.autoTunnelSettings.copy(startOnBoot = to))
    }

    fun setDebounceDelay(to: Int) = intent {
        autoTunnelRepository.upsert(state.autoTunnelSettings.copy(debounceDelaySeconds = to))
    }

    fun setPreferredMobileDataTunnel(tunnel: TunnelConfig?) = intent {
        tunnelsRepository.updateMobileDataTunnel(tunnel)
    }

    fun setPreferredEthernetTunnel(tunnel: TunnelConfig?) = intent {
        tunnelsRepository.updateEthernetTunnel(tunnel)
    }

    fun addTunnelNetwork(tunnel: TunnelConfig, ssid: String) = intent {
        tunnelsRepository.save(
            tunnel.copy(tunnelNetworks = tunnel.tunnelNetworks.toMutableSet().apply { add(ssid) })
        )
    }

    fun removeTunnelNetwork(tunnel: TunnelConfig, ssid: String) = intent {
        tunnelsRepository.save(
            tunnel.copy(
                tunnelNetworks = tunnel.tunnelNetworks.toMutableSet().apply { remove(ssid) }
            )
        )
    }

    fun setWifiDetectionMethod(method: WifiDetectionMethod) = intent {
        when (method) {
            WifiDetectionMethod.ROOT -> {
                val accepted = rootShellUtils.requestRoot()
                val message =
                    if (!accepted) StringValue.StringResource(R.string.error_root_denied)
                    else StringValue.StringResource(R.string.root_accepted)
                postSideEffect(GlobalSideEffect.Snackbar(message))
                if (!accepted) return@intent
            }
            WifiDetectionMethod.SHIZUKU -> {
                requestShizuku()
                return@intent
            }
            else -> Unit
        }
        autoTunnelRepository.upsert(state.autoTunnelSettings.copy(wifiDetectionMethod = method))
    }

    private fun requestShizuku() = intent {
        Shizuku.addRequestPermissionResultListener(
            Shizuku.OnRequestPermissionResultListener { _: Int, grantResult: Int ->
                if (grantResult != PERMISSION_GRANTED) return@OnRequestPermissionResultListener
                intent {
                    autoTunnelRepository.upsert(
                        state.autoTunnelSettings.copy(
                            wifiDetectionMethod = WifiDetectionMethod.SHIZUKU
                        )
                    )
                }
            }
        )
        try {
            if (Shizuku.checkSelfPermission() != PERMISSION_GRANTED) Shizuku.requestPermission(123)
            autoTunnelRepository.upsert(
                state.autoTunnelSettings.copy(wifiDetectionMethod = WifiDetectionMethod.SHIZUKU)
            )
        } catch (_: Exception) {
            postSideEffect(
                GlobalSideEffect.Snackbar(StringValue.StringResource(R.string.shizuku_not_detected))
            )
        }
    }
}
