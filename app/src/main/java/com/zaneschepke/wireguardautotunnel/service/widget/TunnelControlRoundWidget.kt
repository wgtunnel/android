package com.zaneschepke.wireguardautotunnel.service.widget

import android.widget.RemoteViews
import com.zaneschepke.wireguardautotunnel.R

/**
 * Circular variant of [TunnelControlWidget], for home screens with round app icons. It mimics a
 * launcher icon: a circle with the label underneath. Tapping the circle toggles the tunnel; tapping
 * the label opens the app. The circle is an ImageView scaled with fitCenter, so it stays round
 * whatever the cell's aspect ratio.
 */
class TunnelControlRoundWidget : TunnelControlWidget() {

    override val layoutRes: Int = R.layout.widget_tunnel_control_round

    // The label sits outside the circle like an app label, so it can be its own tap target.
    override val labelOpensApp: Boolean = true

    override fun RemoteViews.applyBackground(background: Background) {
        val res =
            when (background) {
                Background.INACTIVE -> R.drawable.widget_circle_inactive
                Background.PENDING -> R.drawable.widget_circle_pending
                Background.ACTIVE -> R.drawable.widget_circle_active
                Background.ERROR -> R.drawable.widget_circle_error
            }
        setImageViewResource(R.id.widget_bg, res)
    }
}
