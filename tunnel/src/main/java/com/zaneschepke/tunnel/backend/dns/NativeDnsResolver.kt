package com.zaneschepke.tunnel.backend.dns

import androidx.annotation.Keep
import com.zaneschepke.tunnel.model.DnsBootstrapResult
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

internal object NativeDnsResolver {

    private const val NATIVE_RESOLUTION_TIMEOUT_MILLIS = 7_000L

    private val callbacks = ConcurrentHashMap<Long, (String) -> Unit>()
    @OptIn(ExperimentalAtomicApi::class) private val nextId = AtomicLong(0)

    private external fun startBootstrapResolution(
        id: Long,
        host: String,
        protocol: String,
        resolvedUpstream: String,
        originalUpstream: String,
        bypass: Int,
    )

    @Keep
    @JvmStatic
    fun onResolutionComplete(id: Long, result: String) {
        val callback = callbacks.remove(id)
        callback?.invoke(result)
    }

    @OptIn(ExperimentalAtomicApi::class)
    suspend fun resolveHostBootstrap(
        host: String,
        protocol: String,
        resolvedUpstream: String,
        originalUpstream: String,
        bypass: Boolean,
    ): DnsBootstrapResult =
        withContext(Dispatchers.IO) {
            val id = nextId.incrementAndFetch()
            val bypassOption = if (bypass) 1 else 0

            try {
                val rawResult =
                    withTimeout(NATIVE_RESOLUTION_TIMEOUT_MILLIS.milliseconds) {
                        suspendCancellableCoroutine { continuation ->
                            callbacks[id] = { raw -> continuation.resumeWith(Result.success(raw)) }

                            continuation.invokeOnCancellation {
                                val removed = callbacks.remove(id)
                                if (removed != null) {
                                    Timber.d("DNS bootstrap cancelled for host=$host id=$id")
                                }
                            }

                            startBootstrapResolution(
                                id = id,
                                host = host,
                                protocol = protocol,
                                resolvedUpstream = resolvedUpstream,
                                originalUpstream = originalUpstream,
                                bypass = bypassOption,
                            )
                        }
                    }

                if (rawResult.startsWith("ERR|")) {
                    throw RuntimeException(rawResult.removePrefix("ERR|"))
                }

                val parts = rawResult.split(";")
                val v4 =
                    parts
                        .firstOrNull { it.startsWith("v4=") }
                        ?.removePrefix("v4=")
                        ?.takeIf { it.isNotBlank() }
                        ?.split(",") ?: emptyList()

                val v6 =
                    parts
                        .firstOrNull { it.startsWith("v6=") }
                        ?.removePrefix("v6=")
                        ?.takeIf { it.isNotBlank() }
                        ?.split(",") ?: emptyList()

                DnsBootstrapResult(ipv4 = v4, ipv6 = v6)
            } catch (e: TimeoutCancellationException) {
                callbacks.remove(id)
                Timber.e(e, "DNS bootstrap timed out for host=$host after 7 seconds")
                throw RuntimeException("DNS bootstrap timed out for $host", e)
            } catch (e: Exception) {
                callbacks.remove(id)
                Timber.w(e, "DNS bootstrap failed for host=$host")
                throw e
            }
        }
}
