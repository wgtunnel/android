package com.zaneschepke.wireguardautotunnel.core.tunnel

import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.data.model.AppMode
import com.zaneschepke.wireguardautotunnel.domain.events.BackendCoreException
import com.zaneschepke.wireguardautotunnel.domain.events.BackendMessage
import com.zaneschepke.wireguardautotunnel.domain.model.LockdownSettings
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelConfig
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface TunnelProvider {
    suspend fun startTunnel(tunnelConfig: TunnelConfig): Result<Unit>

    suspend fun stopTunnel(tunnelId: Int)

    suspend fun stopActiveTunnels()

    suspend fun setLockDown(settings: LockdownSettings)

    suspend fun disableLockDown()

    suspend fun getActiveConfig(tunnelId: Int): ActiveConfig?

    suspend fun changeAppMode(newMode: AppMode): Result<Unit>

    val backendStatus: StateFlow<BackendStatus>
    val errorEvents: SharedFlow<Pair<String?, BackendCoreException>>
    val messageEvents: SharedFlow<Pair<String?, BackendMessage>>
}
