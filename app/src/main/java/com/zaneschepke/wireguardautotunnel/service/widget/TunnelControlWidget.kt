package com.zaneschepke.wireguardautotunnel.service.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.di.Scope
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.ui.state.DisplayTunnelState
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import timber.log.Timber

/**
 * 1x1 home screen widget that shows the current tunnel state and toggles tunnels on tap, using the
 * same logic as the Quick Settings tile ([TunnelCoordinator.toggleActiveTunnels]).
 *
 * The label shows the active tunnel name, or when off, the tunnel a tap will start. The background
 * color shows the connection state: green when connected, yellow while connecting, red on handshake
 * failure, dark when off.
 */
class TunnelControlWidget : AppWidgetProvider(), KoinComponent {

    private val tunnelRepository: TunnelRepository by inject()
    private val tunnelCoordinator: TunnelCoordinator by inject()
    private val applicationScope: CoroutineScope by lazy { get(named(Scope.APPLICATION)) }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        applicationScope.launch {
            try {
                render(context, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                Timber.w(e, "Tunnel widget update failed")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return

        val tunnels = withContext(Dispatchers.IO) { tunnelRepository.userTunnelsFlow.firstOrNull() }
        val active = tunnelCoordinator.backendStatus.value.activeTunnels

        val views = RemoteViews(context.packageName, R.layout.widget_tunnel_control)

        when {
            tunnels.isNullOrEmpty() -> {
                val description = context.getString(R.string.widget_no_tunnels)
                views.bind(
                    background = R.drawable.widget_background_inactive,
                    icon = R.drawable.vpn_off,
                    label = context.getString(R.string.widget_off),
                    description = description,
                )
                // Nothing to toggle, so open the app instead.
                views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
            }

            active.isNotEmpty() -> {
                val activeConfigs = tunnels.filter { active.containsKey(it.id) }
                val states = active.values.map { DisplayTunnelState.from(it) }
                val label =
                    if (activeConfigs.size == 1) {
                        activeConfigs.first().name
                    } else {
                        val tunnelsLabel =
                            context.getString(R.string.tunnels).lowercase(Locale.getDefault())
                        "${active.size} $tunnelsLabel"
                    }
                // Show the "worst" state across active tunnels.
                val state =
                    when {
                        states.any { it == DisplayTunnelState.HandshakeFailure } ->
                            DisplayTunnelState.HandshakeFailure
                        states.any { it != DisplayTunnelState.Connected } ->
                            states.first { it != DisplayTunnelState.Connected }
                        else -> DisplayTunnelState.Connected
                    }
                views.bind(
                    background = backgroundFor(state),
                    icon = R.drawable.vpn_on,
                    label = label,
                    description = "$label • ${state.asLocalizedString(context)}",
                )
                views.setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context))
            }

            else -> {
                // Name what a tap will start, falling back to "Off" if nothing would start.
                val next = tunnelCoordinator.tunnelsToStartOnToggle()
                val label =
                    when (next.size) {
                        0 -> context.getString(R.string.widget_off)
                        1 -> next.first().name
                        else -> {
                            val tunnelsLabel =
                                context.getString(R.string.tunnels).lowercase(Locale.getDefault())
                            "${next.size} $tunnelsLabel"
                        }
                    }
                val name = if (next.isEmpty()) context.getString(R.string.tunnel_control) else label
                views.bind(
                    background = R.drawable.widget_background_inactive,
                    icon = R.drawable.vpn_off,
                    label = label,
                    description =
                        "$name • ${context.getString(R.string.tunnel_state_disconnected)}",
                )
                views.setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context))
            }
        }

        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    private fun RemoteViews.bind(
        @DrawableRes background: Int,
        @DrawableRes icon: Int,
        label: String,
        description: String,
    ) {
        setInt(R.id.widget_root, "setBackgroundResource", background)
        setImageViewResource(R.id.widget_icon, icon)
        setTextViewText(R.id.widget_title, label)
        setContentDescription(R.id.widget_root, description)
    }

    @DrawableRes
    private fun backgroundFor(state: DisplayTunnelState): Int =
        when (state) {
            DisplayTunnelState.Connected -> R.drawable.widget_background_active
            DisplayTunnelState.HandshakeFailure -> R.drawable.widget_background_error
            DisplayTunnelState.ResolvingDns,
            DisplayTunnelState.EstablishingConnection -> R.drawable.widget_background_pending
            DisplayTunnelState.Disconnected -> R.drawable.widget_background_inactive
        }

    private fun togglePendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_TOGGLE,
            Intent(context, TunnelWidgetToggleReceiver::class.java).apply {
                action = TunnelWidgetToggleReceiver.ACTION_TOGGLE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openAppPendingIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return launch?.let {
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    companion object {
        private const val REQUEST_TOGGLE = 7001
        private const val REQUEST_OPEN_APP = 7002

        /** Re-renders all placed widgets. Cheap no-op when no widget is on the home screen. */
        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val component = ComponentName(context, TunnelControlWidget::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) return
                context.sendBroadcast(
                    Intent(context, TunnelControlWidget::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
                .onFailure { Timber.w(it, "Tunnel widget refresh failed") }
        }
    }
}
