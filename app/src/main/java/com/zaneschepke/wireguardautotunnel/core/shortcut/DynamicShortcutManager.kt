package com.zaneschepke.wireguardautotunnel.core.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.zaneschepke.wireguardautotunnel.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class DynamicShortcutManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : ShortcutManager {
    override suspend fun addShortcuts() {
        withContext(ioDispatcher) {
            ShortcutManagerCompat.setDynamicShortcuts(context, createShortcuts())
        }
    }

    override suspend fun removeShortcuts() {
        withContext(ioDispatcher) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, createShortcuts().map { it.id })
        }
    }

    private fun createShortcuts(): List<ShortcutInfoCompat> {
        return listOf(
            buildShortcut(
                context.getString(R.string.vpn_off),
                context.getString(R.string.vpn_off),
                context.getString(R.string.vpn_off),
                intent =
                    Intent(context, ShortcutsActivity::class.java).apply {
                        putExtra("className", "WireGuardTunnelService")
                        action = ShortcutsActivity.Action.STOP.name
                    },
                shortcutIcon = R.drawable.vpn_off,
            ),
            buildShortcut(
                context.getString(R.string.vpn_on),
                context.getString(R.string.vpn_on),
                context.getString(R.string.vpn_on),
                intent =
                    Intent(context, ShortcutsActivity::class.java).apply {
                        putExtra("className", "WireGuardTunnelService")
                        action = ShortcutsActivity.Action.START.name
                    },
                shortcutIcon = R.drawable.vpn_on,
            ),
            buildShortcut(
                context.getString(R.string.start_auto),
                context.getString(R.string.start_auto),
                context.getString(R.string.start_auto),
                intent =
                    Intent(context, ShortcutsActivity::class.java).apply {
                        putExtra("className", "WireGuardConnectivityWatcherService")
                        action = ShortcutsActivity.Action.START.name
                    },
                shortcutIcon = R.drawable.auto_play,
            ),
            buildShortcut(
                context.getString(R.string.stop_auto),
                context.getString(R.string.stop_auto),
                context.getString(R.string.stop_auto),
                intent =
                    Intent(context, ShortcutsActivity::class.java).apply {
                        putExtra("className", "WireGuardConnectivityWatcherService")
                        action = ShortcutsActivity.Action.STOP.name
                    },
                shortcutIcon = R.drawable.auto_pause,
            ),
        )
    }

    private fun buildShortcut(
        id: String,
        shortLabel: String,
        longLabel: String,
        intent: Intent,
        shortcutIcon: Int,
    ): ShortcutInfoCompat {
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIntent(intent)
            .setIcon(IconCompat.createWithResource(context, shortcutIcon))
            .build()
    }
}
