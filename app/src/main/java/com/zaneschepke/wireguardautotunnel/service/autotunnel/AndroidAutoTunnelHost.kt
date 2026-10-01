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
    private val suspendedIds: () -> Set<Long> = { emptySet() },
) : AutoTunnelHost {

    override suspend fun <T> exclusively(block: suspend (TunnelActions) -> T): T =
        tunnelCoordinator.exclusively { locked ->
            val actions =
                CoolingDownTunnelActions(
                    delegate =
                        object : TunnelActions {
                            override suspend fun start(id: Long) {
                                val config = tunnelsRepository.getById(id.toInt())
                                if (config == null) {
                                    Timber.w(
                                        "Auto tunnel wanted tunnel $id but it no longer exists"
                                    )
                                    return
                                }
                                Timber.d("Starting tunnel: ${config.name}")
                                locked.start(config, TunnelActionSource.AUTO_TUNNEL)
                            }

                            override suspend fun stop(id: Long) {
                                Timber.d("Stopping tunnel: $id")
                                locked.stop(id.toInt(), TunnelActionSource.AUTO_TUNNEL)
                            }
                        },
                    suspendedIds = suspendedIds,
                )
            try {
                block(actions)
            } finally {
                actions.finish()
            }
        }
}

/**
 * Holds stops until the starts in the same pass are known. The reconciler stops first and starts
 * second. If the tunnel it wants to start is cooling down, applying the stop would tear down the
 * tunnel that is still up, so both are dropped.
 */
internal class CoolingDownTunnelActions(
    private val delegate: TunnelActions,
    private val suspendedIds: () -> Set<Long>,
) : TunnelActions {
    private val deferredStops = mutableListOf<Long>()
    private var blockedReplacement = false
    private var started = false

    override suspend fun start(id: Long) {
        if (id in suspendedIds()) {
            blockedReplacement = true
            deferredStops.clear()
            Timber.d("Skipping start of cooling-down tunnel $id")
            return
        }
        flushStops()
        started = true
        delegate.start(id)
    }

    override suspend fun stop(id: Long) {
        if (blockedReplacement) return
        if (started) {
            delegate.stop(id)
        } else {
            deferredStops += id
        }
    }

    suspend fun finish() {
        if (!blockedReplacement) {
            flushStops()
        }
    }

    private suspend fun flushStops() {
        val pending = deferredStops.toList()
        deferredStops.clear()
        pending.forEach { delegate.stop(it) }
    }
}
