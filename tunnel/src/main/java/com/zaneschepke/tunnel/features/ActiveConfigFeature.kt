package com.zaneschepke.tunnel.features

import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.RunningTunnel
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class ActiveConfigFeature {

    suspend fun monitor(
        tunnelId: Int,
        getRawActiveConfig: (Int) -> String?,
        statusUpdater: (Int, ActiveConfig?) -> Unit
    ) = coroutineScope {
        while (isActive) {
            try {
                val rawConfig = getRawActiveConfig(tunnelId)
                val activeConfig = rawConfig?.let { ActiveConfig.parseFromIpc(it) }
                statusUpdater(tunnelId, activeConfig)
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch active config for tunnel $tunnelId")
            }
            delay(1.seconds)
        }
    }
}