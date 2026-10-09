package com.zaneschepke.wireguardautotunnel.service.tile

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import timber.log.Timber

object AutoTunnelTileRefresher : TileRefresher {
    override fun refresh(context: Context) {
        // requestListeningState throws IllegalArgumentException on some OEMs andAndroid 13+ devices
        // when
        // called from a work profile
        runCatching {
            TileService.requestListeningState(
                context,
                ComponentName(context, AutoTunnelControlTile::class.java),
            )
        }
            .onFailure { Timber.w(it, "Auto-tunnel tile refresh failed") }
    }
}
