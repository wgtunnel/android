package com.zaneschepke.tunnel.service

import android.content.Context
import android.content.Intent
import com.zaneschepke.tunnel.ProxyBackend
import com.zaneschepke.tunnel.util.BackendException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

internal class ServiceHolder(val context: Context) {

    internal val uapiPath = context.dataDir.absolutePath

    private val _vpnService = MutableStateFlow<VpnService?>(null)
    val vpnServiceFlow: StateFlow<VpnService?> = _vpnService.asStateFlow()
    private val _tunnelService = MutableStateFlow<TunnelService?>(null)
    val tunnelServiceFlow: StateFlow<TunnelService?> = _tunnelService.asStateFlow()

    fun set(service: VpnService) {
        _vpnService.value = service
        ProxyBackend.setSocketProtector(service)
    }

    fun set(service: TunnelService) {
        _tunnelService.value = service
    }

    fun clearVpnService() {
        ProxyBackend.setSocketProtector(null)
        _vpnService.value = null
    }

    fun clearTunnelService() {
        _tunnelService.value = null
    }

    suspend fun getVpnService(): VpnService {
        if (android.net.VpnService.prepare(context) != null) {
            throw BackendException.Unauthorized("Permission unavailable to use VpnService")
        }

        if (_vpnService.value == null) {
            VpnService.start(context, VpnService::class.java)
        }

        return try {
            withTimeout(3_000L.milliseconds) { vpnServiceFlow.filterNotNull().first() }
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Timed out getting VpnService")
            throw BackendException.InternalError("Failed to get VpnService")
        }
    }

    suspend fun getTunnelService(): TunnelService {
        if (_tunnelService.value == null) {
            context.startForegroundService(Intent(context, TunnelService::class.java))
        }

        return try {
            withTimeout(3_000L.milliseconds) { tunnelServiceFlow.filterNotNull().first() }
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Timed out getting TunnelService")
            throw BackendException.InternalError("Failed to get TunnelService")
        }
    }

    suspend fun stopVpnService() {
        val service = _vpnService.value ?: return
        try {
            service.shutdown()
            withTimeoutOrNull(1_500L.milliseconds) { vpnServiceFlow.first { it == null } }
        } finally {
            clearVpnService()
        }
    }

    suspend fun stopTunnelService() {
        val service = _tunnelService.value ?: return
        try {
            service.shutdown()
            withTimeoutOrNull(1_500L.milliseconds) { tunnelServiceFlow.first { it == null } }
        } finally {
            clearTunnelService()
        }
    }

    /**
     * Gets the VpnService and starts if needed while ensuring the protector is registered. This is
     * needed before any native call that uses NewStdNetBindWithControl.
     */
    suspend fun ensureVpnProtectorRegistered(): VpnService {
        val service = getVpnService()
        ProxyBackend.setSocketProtector(service)
        // Small delay to give JNI time to propagate on slow devices
        delay(50.milliseconds)
        return service
    }

    companion object {
        const val SPECIAL_USE_SERVICE_TYPE_ID = 1 shl 30
        const val DEFAULT_MTU = 1280
        // for consumer to set AOVPN callback
        var alwaysOnCallback: VpnService.AlwaysOnCallback? = null
    }
}
