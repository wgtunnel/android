package com.zaneschepke.tunnel.backend

import android.content.Context
import android.content.Intent
import com.zaneschepke.tunnel.service.TunnelService
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.tunnel.util.BackendException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import timber.log.Timber

internal class ServiceHolder(private val context: Context) {

    internal val uapiPath = context.dataDir.absolutePath

    fun set(service: VpnService) {
        vpnService.complete(service)
    }

    fun set(service: TunnelService) {
        tunnelService.complete(service)
    }

    fun getVpnService(): VpnService {

        vpnService.getNow(null)?.let {
            return it
        }

        try {
            if (android.net.VpnService.prepare(context) != null) {
                throw BackendException.Unauthorized("Permission unavailable to use VpnService")
            }

            context.startForegroundService(Intent(context, VpnService::class.java))
        } catch (e: Exception) {
            Timber.e(e, "Error starting VPN service")
        }

        return try {
            vpnService.get(2, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            Timber.e(e, "Timed out getting VpnService")
            throw BackendException.InternalError("Failed to get VpnService")
        }
    }

    fun getTunnelService(): TunnelService {

        tunnelService.getNow(null)?.let {
            return it
        }

        try {
            context.startForegroundService(Intent(context, TunnelService::class.java))
        } catch (e: Exception) {
            Timber.e(e, "Error starting TunnelService")
        }

        return try {
            tunnelService.get(2, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            Timber.e(e, "Timed out getting TunnelService")
            throw BackendException.InternalError("Failed to get TunnelService")
        }
    }

    fun stopVpnService() {
        val service = vpnService.getNow(null) ?: return

        Timber.d("Stopping VpnService")

        service.stopSelf()
    }

    fun stopTunnelService() {
        val service = tunnelService.getNow(null) ?: return

        Timber.d("Stopping TunnelService")

        service.stopSelf()
    }

    companion object {
        const val DEFAULT_MTU = 1280
        // for consumer to set AOVPN callback
        var alwaysOnCallback: VpnService.AlwaysOnCallback? = null
        @Volatile var vpnService = CompletableFuture<VpnService>()
        @Volatile var tunnelService = CompletableFuture<TunnelService>()
    }
}
