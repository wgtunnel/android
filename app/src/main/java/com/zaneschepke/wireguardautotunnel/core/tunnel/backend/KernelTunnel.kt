package com.zaneschepke.wireguardautotunnel.core.tunnel.backend

import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.WgQuickBackend
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.enums.BackendMode
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelStatus
import com.zaneschepke.wireguardautotunnel.domain.events.DnsFailure
import com.zaneschepke.wireguardautotunnel.domain.events.InvalidConfig
import com.zaneschepke.wireguardautotunnel.domain.events.KernelTunnelName
import com.zaneschepke.wireguardautotunnel.domain.events.KernelWireguardNotSupported
import com.zaneschepke.wireguardautotunnel.domain.events.UnknownError
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelStatistics
import com.zaneschepke.wireguardautotunnel.domain.state.WireGuardStatistics
import com.zaneschepke.wireguardautotunnel.util.extensions.asTunnelState
import com.zaneschepke.wireguardautotunnel.util.extensions.toBackendCoreException
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class KernelTunnel(private val runConfigHelper: RunConfigHelper, private val backend: Backend) :
    TunnelBackend {

    private val runtimeTunnels = ConcurrentHashMap<Int, Tunnel>()

    private fun validateWireGuardInterfaceName(name: String): Result<Unit> {
        if (name.isEmpty() || name.length > 15)
            return Result.failure(KernelTunnelName(R.string.kernel_name_error))
        if (name == "." || name == "..") {
            return Result.failure(KernelTunnelName(R.string.kernel_name_dots))
        }
        val pattern = Pattern.compile("^[a-zA-Z0-9_=+.-]{1,15}$")
        if (!pattern.matcher(name).matches()) {
            return Result.failure(KernelTunnelName(R.string.kernel_name_special_characters))
        }
        return Result.success(Unit)
    }

    override fun tunnelStateFlow(tunnelConfig: TunnelConfig): Flow<TunnelStatus> = callbackFlow {
        if (!WgQuickBackend.hasKernelSupport()) throw KernelWireguardNotSupported()
        validateWireGuardInterfaceName(tunnelConfig.name).onFailure { throw it }

        val stateChannel = Channel<Tunnel.State>()

        val runtimeTunnel = RuntimeWgTunnel(tunnelConfig, stateChannel)
        runtimeTunnels[tunnelConfig.id] = runtimeTunnel

        val consumerJob = launch {
            stateChannel.consumeAsFlow().collect { state -> trySend(state.asTunnelState()) }
        }

        try {
            val runConfig = runConfigHelper.buildWgRunConfig(tunnelConfig)
            backend.setState(runtimeTunnel, Tunnel.State.UP, runConfig)
        } catch (e: TimeoutCancellationException) {
            Timber.Forest.e("Startup timed out for ${tunnelConfig.name}")
            throw DnsFailure()
        } catch (e: BackendException) {
            throw e.toBackendCoreException()
        } catch (e: IllegalArgumentException) {
            Timber.Forest.e(e, "Invalid backend arguments")
            throw InvalidConfig()
        } catch (e: Exception) {
            Timber.Forest.e(e, "Error while setting tunnel state")
            throw UnknownError()
        }

        awaitClose {
            try {
                backend.setState(runtimeTunnel, Tunnel.State.DOWN, null)
            } catch (e: BackendException) {
                // Errors are emitted by caller (lifecycle manager)
            } finally {
                consumerJob.cancel()
                stateChannel.close()
                runtimeTunnels.remove(tunnelConfig.id)
                trySend(TunnelStatus.Down)
            }
        }
    }

    override fun getStatistics(tunnelId: Int): TunnelStatistics? {
        return try {
            val runtimeTunnel = runtimeTunnels[tunnelId] ?: return null
            WireGuardStatistics(backend.getStatistics(runtimeTunnel))
        } catch (e: Exception) {
            Timber.Forest.e(e, "Failed to get stats for $tunnelId")
            null
        }
    }

    override fun setBackendMode(backendMode: BackendMode) {
        Timber.Forest.w("Not yet implemented for kernel")
    }

    override fun getBackendMode(): BackendMode {
        return BackendMode.Inactive
    }

    override fun handleDnsReresolve(tunnelConfig: TunnelConfig): Boolean {
        throw NotImplementedError()
    }

    override suspend fun runningTunnelNames(): Set<String> {
        return backend.runningTunnelNames
    }

    override suspend fun forceStopTunnel(tunnelId: Int) {
        val runtimeTunnel = runtimeTunnels[tunnelId] ?: return
        try {
            backend.setState(runtimeTunnel, Tunnel.State.DOWN, null)
        } catch (e: BackendException) {
            Timber.Forest.e(e, "Force stop failed for $tunnelId")
        } finally {
            runtimeTunnels.remove(tunnelId)
        }
    }
}
