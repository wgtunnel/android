package com.zaneschepke.wireguardautotunnel.core.orchestration

import android.content.Intent
import com.zaneschepke.wireguardautotunnel.core.shortcut.ShortcutContract
import com.zaneschepke.wireguardautotunnel.domain.repository.GeneralSettingRepository
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository

class ShortcutCoordinator(
    private val settingsRepository: GeneralSettingRepository,
    private val tunnelsRepository: TunnelRepository,
    private val tunnelCoordinator: TunnelCoordinator,
    private val autoTunnelCoordinator: AutoTunnelCoordinator,
) {

    suspend fun handle(intent: Intent) {

        val settings = settingsRepository.getGeneralSettings()

        if (!settings.isShortcutsEnabled) return

        val shortcutType =
            intent.getStringExtra(ShortcutContract.EXTRA_SHORTCUT_TYPE)
                ?: legacyShortcutType(intent)

        when (shortcutType) {
            ShortcutContract.ShortcutType.TUNNEL.value -> {
                handleTunnelShortcut(intent)
            }

            ShortcutContract.ShortcutType.AUTO_TUNNEL.value -> {
                handleAutoTunnelShortcut(intent)
            }
        }
    }

    private suspend fun handleAutoTunnelShortcut(intent: Intent) {

        when (intent.action) {
            ShortcutContract.Action.START.name -> {
                autoTunnelCoordinator.enable()
            }

            ShortcutContract.Action.STOP.name -> {
                autoTunnelCoordinator.disable()
            }
        }
    }

    private fun legacyShortcutType(intent: Intent): String? {

        return when (intent.getStringExtra(ShortcutContract.EXTRA_CLASS_NAME)) {
            ShortcutContract.Legacy.AUTO_TUNNEL_SERVICE_CLASS_NAME,
            ShortcutContract.Legacy.AUTO_TUNNEL_SERVICE_NAME ->
                ShortcutContract.ShortcutType.AUTO_TUNNEL.value

            ShortcutContract.Legacy.TUNNEL_PROVIDER_NAME,
            ShortcutContract.Legacy.TUNNEL_SERVICE_NAME ->
                ShortcutContract.ShortcutType.TUNNEL.value

            else -> null
        }
    }

    private suspend fun handleTunnelShortcut(intent: Intent) {

        val tunnelName = intent.getStringExtra(ShortcutContract.EXTRA_TUNNEL_NAME)

        val tunnel =
            tunnelName?.let { tunnelsRepository.findByTunnelName(it) }
                ?: tunnelsRepository.getDefaultTunnel()

        tunnel ?: return

        when (intent.action) {
            ShortcutContract.Action.START.name -> {
                tunnelCoordinator.startTunnel(config = tunnel)
            }

            ShortcutContract.Action.STOP.name -> {
                tunnelCoordinator.stopActiveTunnels()
            }
        }
    }
}
