package com.zaneschepke.wireguardautotunnel.core.tunnel.backend

import com.zaneschepke.wireguardautotunnel.domain.enums.BackendMode
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelStatus
import com.zaneschepke.wireguardautotunnel.domain.events.DnsFailure
import com.zaneschepke.wireguardautotunnel.domain.events.InvalidConfig
import com.zaneschepke.wireguardautotunnel.domain.events.ServiceNotRunning
import com.zaneschepke.wireguardautotunnel.domain.events.UnknownError
import com.zaneschepke.wireguardautotunnel.domain.events.VpnUnauthorized
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.domain.state.AmneziaStatistics
import com.zaneschepke.wireguardautotunnel.domain.state.TunnelStatistics
import com.zaneschepke.wireguardautotunnel.util.extensions.asAmBackendMode
import com.zaneschepke.wireguardautotunnel.util.extensions.asBackendMode
import com.zaneschepke.wireguardautotunnel.util.extensions.asTunnelState
import com.zaneschepke.wireguardautotunnel.util.extensions.toBackendCoreException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.Backend
import org.amnezia.awg.backend.BackendException
import org.amnezia.awg.backend.Tunnel
import timber.log.Timber

class UserspaceTunnel(private val backend: Backend, private val runConfigHelper: RunConfigHelper) :
    TunnelBackend {

    private val runtimeTunnels = ConcurrentHashMap<Int, Tunnel>()

    override fun tunnelStateFlow(tunnelConfig: TunnelConfig): Flow<TunnelStatus> = callbackFlow {
        val stateChannel = Channel<Tunnel.State>()

        val runtimeTunnel = RuntimeAwgTunnel(tunnelConfig, stateChannel)
        runtimeTunnels[tunnelConfig.id] = runtimeTunnel

        val consumerJob = launch {
            stateChannel.consumeAsFlow().collect { awgState -> trySend(awgState.asTunnelState()) }
        }

        try {
            val runConfig = runConfigHelper.buildAmRunConfig(tunnelConfig)
            backend.setState(runtimeTunnel, Tunnel.State.UP, runConfig)
        } catch (_: TimeoutCancellationException) {
            Timber.e("Startup timed out for ${tunnelConfig.name} (likely DNS hang)")
            throw DnsFailure()
        } catch (e: BackendException) {
            throw e.toBackendCoreException()
        } catch (_: IllegalArgumentException) {
            throw InvalidConfig()
        } catch (e: Exception) {
            Timber.e(e, "Error while setting tunnel state")
            throw UnknownError()
        }

        awaitClose {
            try {
                backend.setState(runtimeTunnel, Tunnel.State.DOWN, null)
            } catch (e: BackendException) {
                // Errors emitted by caller
            } finally {
                consumerJob.cancel()
                stateChannel.close()
                runtimeTunnels.remove(tunnelConfig.id)
                trySend(TunnelStatus.Down)
            }
        }
    }

    override fun setBackendMode(backendMode: BackendMode) {
        Timber.d("Setting backend mode: $backendMode")
        try {
            backend.backendMode = backendMode.asAmBackendMode()
        } catch (e: BackendException) {
            throw e.toBackendCoreException()
        } catch (_: IOException) {
            throw VpnUnauthorized()
        }
    }

    override fun getBackendMode(): BackendMode {
        return backend.backendMode.asBackendMode()
    }

    override fun handleDnsReresolve(tunnelConfig: TunnelConfig): Boolean {
        val tunnel = runtimeTunnels[tunnelConfig.id] ?: throw ServiceNotRunning()
        return backend.resolveDDNS(tunnelConfig.toAmConfig(), tunnel.isIpv4ResolutionPreferred)
    }

    override suspend fun runningTunnelNames(): Set<String> {
        return backend.runningTunnelNames
    }

    override fun getStatistics(tunnelId: Int): TunnelStatistics? {
        return try {
            val runtimeTunnel = runtimeTunnels[tunnelId] ?: return null
            AmneziaStatistics(backend.getStatistics(runtimeTunnel))
        } catch (e: Exception) {
            Timber.e(e, "Failed to get stats for $tunnelId")
            null
        }
    }

    override suspend fun forceStopTunnel(tunnelId: Int) {
        val runtimeTunnel = runtimeTunnels[tunnelId] ?: return
        try {
            backend.setState(runtimeTunnel, Tunnel.State.DOWN, null)
        } catch (e: BackendException) {
            Timber.e(e, "Force stop failed for $tunnelId")
        } finally {
            runtimeTunnels.remove(tunnelId)
        }
    }
}
