package com.zaneschepke.wireguardautotunnel.service.autotunnel

import com.wgtunnel.backend.autotunnel.AutoTunnelHost
import com.wgtunnel.backend.autotunnel.TunnelActions
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.domain.enums.TunnelActionSource
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import timber.log.Timber

class AndroidAutoTunnelHost(
    private val tunnelCoordinator: TunnelCoordinator,
    private val tunnelsRepository: TunnelRepository,
) : AutoTunnelHost {

    override suspend fun <T> exclusively(block: suspend (TunnelActions) -> T): T =
        tunnelCoordinator.exclusively { locked ->
            block(
                object : TunnelActions {
                    override suspend fun start(id: Long) {
                        val config = tunnelsRepository.getById(id.toInt())
                        if (config == null) {
                            Timber.w("Auto tunnel wanted tunnel $id but it no longer exists")
                            return
                        }
                        Timber.d("Starting tunnel: ${config.name}")
                        locked.start(config, TunnelActionSource.AUTO_TUNNEL)
                    }

                    override suspend fun stop(id: Long) {
                        Timber.d("Stopping tunnel: $id")
                        locked.stop(id.toInt(), TunnelActionSource.AUTO_TUNNEL)
                    }
                }
            )
        }
}
