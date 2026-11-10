package com.zaneschepke.wireguardautotunnel.service.widget

import android.widget.RemoteViews
import com.zaneschepke.wireguardautotunnel.R

/**
 * Circular variant of [TunnelControlWidget], for home screens with round app icons. The circle is
 * an ImageView scaled with fitCenter, so it stays round whatever the cell's aspect ratio.
 */
class TunnelControlRoundWidget : TunnelControlWidget() {

    override val layoutRes: Int = R.layout.widget_tunnel_control_round

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
