package com.zaneschepke.wireguardautotunnel.service.tile

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService

object TunnelTileRefresher : TileRefresher {
    override fun refresh(context: Context) {
        TileService.requestListeningState(
            context,
            ComponentName(context, TunnelControlTile::class.java),
        )
    }
}
