package com.zaneschepke.wireguardautotunnel.service.tile

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import timber.log.Timber

object TunnelTileRefresher : TileRefresher {
    override fun refresh(context: Context) {
        // requestListeningState throws IllegalArgumentException on some OEMs andAndroid 13+ devices
        // when
        // called from a work profile
        runCatching {
            TileService.requestListeningState(
                context,
                ComponentName(context, TunnelControlTile::class.java),
            )
        }
            .onFailure { Timber.w(it, "Tunnel tile refresh failed") }
    }
}
