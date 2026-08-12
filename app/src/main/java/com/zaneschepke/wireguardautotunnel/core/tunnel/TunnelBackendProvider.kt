package com.zaneschepke.wireguardautotunnel.core.tunnel

import com.wgtunnel.backend.Backend
import com.wgtunnel.backend.Tunnel
import com.wgtunnel.backend.model.BackendMode
import com.wgtunnel.backend.model.dns.TunnelDnsConfig
import com.wgtunnel.backend.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.domain.model.LockdownSettings
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
class TunnelBackendProvider(
    private val backend: Backend,
    applicationScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
) : TunnelProvider {

    override val backendStatus: StateFlow<BackendStatus> =
        backend.status.stateIn(
            scope = applicationScope.plus(ioDispatcher),
            started = SharingStarted.Eagerly,
            initialValue = BackendStatus(),
        )

    override val events = backend.events

    override suspend fun startTunnel(
        tunnel: Tunnel,
        mode: BackendMode,
        tunnelDnsConfig: TunnelDnsConfig?,
    ): Result<Unit> {
        return backend.start(tunnel = tunnel, mode = mode, tunnelDnsConfig)
    }

    override suspend fun stopTunnel(tunnelId: Int): Result<Unit> {
        return backend.stop(tunnelId)
    }

    override suspend fun stopActiveTunnels(): Result<Unit> {
        return backend.stopAllActiveTunnels()
    }

    override suspend fun setLockDown(settings: LockdownSettings): Result<Unit> {
        return backend.setKillSwitch(settings.toKillSwitchConfig())
    }

    override suspend fun disableLockDown(): Result<Unit> {
        return backend.disableKillSwitch()
    }
}
