package com.zaneschepke.tunnel.backend.dns

import android.net.Network
import com.zaneschepke.tunnel.model.DnsBootstrapResult
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

internal class AndroidNetworkResolver(private val network: Network) : PeerResolver {

    override suspend fun resolve(host: String): DnsBootstrapResult =
        withContext(Dispatchers.IO) {
            try {
                val ips =
                    withTimeoutOrNull(DNS_RESOLUTION_TIMEOUT_MILLIS.milliseconds) {
                        network.getAllByName(host).toList()
                    }
                        ?: run {
                            Timber.w("DNS resolution timed out after 2200ms for $host")
                            return@withContext DnsBootstrapResult()
                        }

                Timber.d("Resolution from network bind socket: $ips")

                val v4 = ips.filter { it.address.size == 4 }.map { it.hostAddress }
                val v6 = ips.filter { it.address.size == 16 }.map { it.hostAddress }

                DnsBootstrapResult(v4, v6)
            } catch (e: Exception) {
                Timber.e(e, "System DNS failed to resolve host")
                DnsBootstrapResult()
            }
        }

    companion object {
        private const val DNS_RESOLUTION_TIMEOUT_MILLIS = 5_000L
    }
}
