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

internal class ServiceManager(val context: Context) {

    internal val uapiPath = context.dataDir.absolutePath

    private val _vpnService = MutableStateFlow<VpnService?>(null)
    val vpnServiceFlow: StateFlow<VpnService?> = _vpnService.asStateFlow()
    private val _tunnelService = MutableStateFlow<TunnelService?>(null)
    val tunnelServiceFlow: StateFlow<TunnelService?> = _tunnelService.asStateFlow()

    private val _companionService = MutableStateFlow<VpnCompanionService?>(null)
    val companionServiceFlow: StateFlow<VpnCompanionService?> = _companionService.asStateFlow()

    fun set(service: VpnService) {
        _vpnService.value = service
        ProxyBackend.setSocketProtector(service)
    }

    fun set(service: TunnelService) {
        _tunnelService.value = service
    }

    fun set(service: VpnCompanionService) {
        _companionService.value = service
    }

    fun clearCompanionService() {
        _companionService.value = null
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

        return withTimeoutOrThrow(SERVICE_START_TIMEOUT_MILLIS) {
            vpnServiceFlow.filterNotNull().first()
        }
    }

    suspend fun getCompanionService(): VpnCompanionService {
        if (_companionService.value == null) {
            context.startForegroundService(Intent(context, VpnCompanionService::class.java))
        }

        return withTimeoutOrThrow(SERVICE_START_TIMEOUT_MILLIS) {
            companionServiceFlow.filterNotNull().first()
        }
    }

    suspend fun getTunnelService(): TunnelService {
        if (_tunnelService.value == null) {
            context.startForegroundService(Intent(context, TunnelService::class.java))
        }

        return withTimeoutOrThrow(SERVICE_START_TIMEOUT_MILLIS) {
            tunnelServiceFlow.filterNotNull().first()
        }
    }

    suspend fun stopVpnService() {
        val service = _vpnService.value ?: return
        try {
            service.shutdown()
            withTimeoutOrNull(SERVICE_SHUTDOWN_TIMEOUT_MILLIS.milliseconds) {
                vpnServiceFlow.first { it == null }
            }
        } finally {
            clearVpnService()
        }
    }

    suspend fun stopCompanionService() {
        val service = _companionService.value ?: return
        try {
            service.shutdown()
            withTimeoutOrNull(SERVICE_SHUTDOWN_TIMEOUT_MILLIS.milliseconds) {
                companionServiceFlow.first { it == null }
            }
        } finally {
            clearCompanionService()
        }
    }

    suspend fun stopTunnelService() {
        val service = _tunnelService.value ?: return
        try {
            service.shutdown()
            withTimeoutOrNull(SERVICE_SHUTDOWN_TIMEOUT_MILLIS.milliseconds) {
                tunnelServiceFlow.first { it == null }
            }
        } finally {
            clearTunnelService()
        }
    }

    suspend fun ensureVpnReady(): VpnService {
        // needed for foreground
        getCompanionService()
        val vpnService = getVpnService()
        // we can safely call this again here
        ProxyBackend.setSocketProtector(vpnService)
        delay(JNI_PROP_DELAY_MILLIS.milliseconds)
        return vpnService
    }

    // shuts down the vpn services, protector clean up handled by clearVpnService
    suspend fun ensureVpnShutdown() {
        stopVpnService()
        stopCompanionService()
    }

    private suspend inline fun <T> withTimeoutOrThrow(
        timeoutMs: Long,
        crossinline block: suspend () -> T,
    ): T {
        return try {
            withTimeout(timeoutMs.milliseconds) { block() }
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Timed out waiting for service")
            throw BackendException.InternalError("Failed to acquire service")
        }
    }

    companion object {
        const val JNI_PROP_DELAY_MILLIS = 50L
        const val SERVICE_START_TIMEOUT_MILLIS = 3_000L
        const val SERVICE_SHUTDOWN_TIMEOUT_MILLIS = 1_500L
        const val SPECIAL_USE_SERVICE_TYPE_ID = 1 shl 30
        const val DEFAULT_MTU = 1280
        // for consumer to set AOVPN callback
        var alwaysOnCallback: VpnService.AlwaysOnCallback? = null
    }
}
