package com.zaneschepke.wireguardautotunnel.core.lock

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.core.orchestration.AutoTunnelCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.NotificationActionCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.ShortcutCoordinator
import com.zaneschepke.wireguardautotunnel.core.orchestration.TunnelCoordinator
import com.zaneschepke.wireguardautotunnel.di.Scope
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.ui.theme.WireguardAutoTunnelTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import xyz.teamgravity.pin_lock_compose.PinLock
import xyz.teamgravity.pin_lock_compose.PinManager

/** Asks for the app PIN before running a tunnel action that started outside the app */
class PinGateActivity : ComponentActivity() {

    enum class Action {
        TOGGLE_TUNNELS,
        TOGGLE_AUTO_TUNNEL,
        SHORTCUT,
        NOTIFICATION,
    }

    private val appLockManager: AppLockManager by inject()
    private val settingsRepository: GeneralSettingRepository by inject()
    private val tunnelCoordinator: TunnelCoordinator by inject()
    private val autoTunnelCoordinator: AutoTunnelCoordinator by inject()
    private val shortcutCoordinator: ShortcutCoordinator by inject()
    private val notificationActionCoordinator: NotificationActionCoordinator by inject()
    private val applicationScope: CoroutineScope by inject(named(Scope.APPLICATION))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action =
            intent.getStringExtra(EXTRA_ACTION)?.let { name ->
                Action.entries.firstOrNull { it.name == name }
            }
        if (action == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            if (!appLockManager.isLockRequired()) {
                runAndFinish(action)
                return@launch
            }
            val theme = settingsRepository.getGeneralSettings().theme
            // The PIN pad must never show up in screenshots or recordings
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            enableEdgeToEdge()
            PinManager.initialize(context = this@PinGateActivity)
            setContent {
                WireguardAutoTunnelTheme(theme = theme) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .safeDrawingPadding(),
                    ) {
                        PinLock(
                            title = {
                                Text(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    text = stringResource(R.string.enter_pin),
                                )
                            },
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            onPinCorrect = {
                                appLockManager.unlock()
                                runAndFinish(action)
                            },
                            onPinIncorrect = {
                                Toast.makeText(
                                        this@PinGateActivity,
                                        R.string.incorrect_pin,
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            },
                            onPinCreated = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun runAndFinish(action: Action) {
        val forwarded = IntentCompat.getParcelableExtra(intent, EXTRA_FORWARDED, Intent::class.java)
        // Outlives this activity, which closes right away
        applicationScope.launch {
            when (action) {
                Action.TOGGLE_TUNNELS -> tunnelCoordinator.toggleActiveTunnels()
                Action.TOGGLE_AUTO_TUNNEL -> autoTunnelCoordinator.toggle()
                Action.SHORTCUT -> forwarded?.let { shortcutCoordinator.handle(it) }
                Action.NOTIFICATION -> forwarded?.let { notificationActionCoordinator.handle(it) }
            }
        }
        finish()
    }

    companion object {
        private const val EXTRA_ACTION = "pin_gate_action"
        private const val EXTRA_FORWARDED = "pin_gate_forwarded"

        fun intent(context: Context, action: Action, forwarded: Intent? = null): Intent =
            Intent(context, PinGateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this.action = listOfNotNull(action.name, forwarded?.action).joinToString(":")
                putExtra(EXTRA_ACTION, action.name)
                forwarded?.let { putExtra(EXTRA_FORWARDED, it) }
            }

        fun pendingIntent(
            context: Context,
            requestCode: Int,
            action: Action,
            forwarded: Intent? = null,
        ): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                intent(context, action, forwarded),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
