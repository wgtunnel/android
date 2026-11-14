package com.zaneschepke.wireguardautotunnel.service.tile

import android.annotation.SuppressLint
import android.os.Build
import android.service.quicksettings.TileService
import com.zaneschepke.wireguardautotunnel.core.lock.PinGateActivity

/** Collapses the shade and asks for the app PIN before running the action. */
@SuppressLint("StartActivityAndCollapseDeprecated")
fun TileService.startPinGate(action: PinGateActivity.Action) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startActivityAndCollapse(PinGateActivity.pendingIntent(this, action.ordinal, action))
    } else {
        @Suppress("DEPRECATION") startActivityAndCollapse(PinGateActivity.intent(this, action))
    }
}
