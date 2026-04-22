package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.DnsBoostrapConfig
import com.zaneschepke.tunnel.model.DnsConfig
import com.zaneschepke.tunnel.model.KillSwitchConfig
import com.zaneschepke.tunnel.state.BackendStatus
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import kotlinx.coroutines.flow.Flow
import java.io.File

interface Backend {

    suspend fun start(
        tunnel: Tunnel,
        mode: BackendMode
    ): Result<Unit>

    suspend fun stop(id: Int): Result<Unit>

    suspend fun setKillSwitch(config: KillSwitchConfig): Result<Unit>

    suspend fun disableKillSwitch(): Result<Unit>

    suspend fun setBootstrapDnsConfig(config : DnsBoostrapConfig)

    suspend fun getActiveConfig(id: Int): Result<ActiveConfig?>

    val status: Flow<BackendStatus>

    companion object {
        private const val KERNEL_SUPPORT_PATH = "/sys/module/wireguard"
        fun hasKernelSupport() : Boolean {
            return File(KERNEL_SUPPORT_PATH).exists()
        }
    }
}