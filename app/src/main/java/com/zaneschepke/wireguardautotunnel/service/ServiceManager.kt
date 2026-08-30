package com.zaneschepke.wireguardautotunnel.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.zaneschepke.wireguardautotunnel.service.autotunnel.AutoTunnelService
import com.zaneschepke.wireguardautotunnel.util.permission.NotificationPermissionHelper

class ServiceManager(private val context: Context) {

    fun startAutoTunnelService() {
        context.startForegroundService(Intent(context, AutoTunnelService::class.java))
    }

    fun stopAutoTunnelService() {
        context.stopService(Intent(context, AutoTunnelService::class.java))
    }

    fun hasVpnPermission(): Boolean {
        return VpnService.prepare(context) == null
    }

    fun hasNotificationPermission(): Boolean {
        return NotificationPermissionHelper.isPermissionGranted(context)
    }
}
