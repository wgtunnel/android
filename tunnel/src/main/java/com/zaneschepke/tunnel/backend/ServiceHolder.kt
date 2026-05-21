package com.zaneschepke.tunnel.backend

import android.content.Context
import android.content.Intent
import com.zaneschepke.tunnel.StatusCallback
import com.zaneschepke.tunnel.VpnBackend
import com.zaneschepke.tunnel.service.TunnelService
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.tunnel.state.NativeTunnelStatus
import com.zaneschepke.tunnel.util.BackendException
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

internal class ServiceHolder(private val context: Context) {

    internal val uapiPath = context.dataDir.absolutePath

    @OptIn(ExperimentalAtomicApi::class)
    private val nativeCallbacksRegistered = AtomicBoolean(false)

    private val _nativeStatuses = MutableSharedFlow<NativeTunnelStatus>(extraBufferCapacity = 64)

    val nativeStatuses = _nativeStatuses.asSharedFlow()

    private val statusCallback = StatusCallback { handle, code ->
        val status = NativeTunnelStatus.NativeTunnelStatusCode.from(code)

        if (status == null) {
            Timber.d("Unknown native status code: $code")
            return@StatusCallback
        }

        Timber.d("Native Callback - Handle: $handle, Code: $status")

        _nativeStatuses.tryEmit(NativeTunnelStatus(handle = handle, code = status))
    }

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

    @OptIn(ExperimentalAtomicApi::class)
    fun ensureNativeCallbacksRegistered() {
        if (!nativeCallbacksRegistered.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }

        VpnBackend.setStatusCallback(statusCallback)

        Timber.d("Registered native status callback")
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun maybeUnregisterNativeCallbacks() {
        val vpnAlive = vpnService.getNow(null) != null
        val tunnelAlive = tunnelService.getNow(null) != null

        if (vpnAlive || tunnelAlive) {
            return
        }

        if (!nativeCallbacksRegistered.compareAndSet(expectedValue = true, newValue = false)) {
            return
        }

        VpnBackend.setStatusCallback(null)

        Timber.d("Unregistered native status callback")
    }

    fun clear(service: VpnService) {
        if (vpnService.getNow(null) === service) {
            vpnService = CompletableFuture()
        }

        maybeUnregisterNativeCallbacks()
    }

    fun clear(service: TunnelService) {
        if (tunnelService.getNow(null) === service) {
            tunnelService = CompletableFuture()
        }
        maybeUnregisterNativeCallbacks()
    }

    companion object {
        const val DEFAULT_MTU = 1280
        // for consumer to set AOVPN callback
        var alwaysOnCallback: WeakReference<VpnService.AlwaysOnCallback>? = null
        @Volatile var vpnService = CompletableFuture<VpnService>()
        @Volatile var tunnelService = CompletableFuture<TunnelService>()
    }
}
