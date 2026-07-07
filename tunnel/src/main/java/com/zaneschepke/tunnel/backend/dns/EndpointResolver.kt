package com.zaneschepke.tunnel.backend.dns

import com.zaneschepke.networkmonitor.StableNetworkEngine
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.DnsBoostrapMode
import com.zaneschepke.tunnel.model.DnsBootstrapResult
import com.zaneschepke.tunnel.model.PublicKey
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import timber.log.Timber

class EndpointResolver(
    private val stableNetworkEngine: StableNetworkEngine,
    private val getDnsMode: () -> DnsBoostrapMode,
    private val isKillSwitchEnabled: () -> Boolean,
) {
    suspend fun resolvePeers(mode: BackendMode): Map<PublicKey, DnsBootstrapResult> =
        coroutineScope {
            val peersToResolve = mode.config.peers.filter { !it.isStaticallyConfigured }
            if (peersToResolve.isEmpty()) return@coroutineScope emptyMap()

            val results = mutableMapOf<PublicKey, DnsBootstrapResult>()
            stableNetworkEngine.stableState.first { it?.state?.activeNetwork?.network != null }

            var delayMs = 500L

            while (isActive) {
                val snapshot = stableNetworkEngine.stableState.value?.state
                val network =
                    snapshot?.activeNetwork?.network
                        ?: run {
                            delay(100.milliseconds)
                            continue
                        }

                val dnsMode = getDnsMode()
                val bypassNeeded = mode is BackendMode.Vpn || isKillSwitchEnabled()
                var progressed = false

                for (peer in peersToResolve) {
                    if (results.containsKey(peer.publicKey)) continue
                    val host = peer.endpoint?.substringBeforeLast(":") ?: continue

                    val resolver: PeerResolver =
                        when (dnsMode) {
                            is DnsBoostrapMode.System -> AndroidNetworkResolver(network)
                            is DnsBoostrapMode.Custom ->
                                CustomDnsResolver(dnsMode.config, bypassNeeded, network)
                        }

                    val result = resolver.resolve(host)

                    if (result.ipv4.isNotEmpty() || result.ipv6.isNotEmpty()) {
                        results[peer.publicKey] =
                            result.copy(
                                ipv6 = result.ipv6.map { if (it.startsWith("[")) it else "[$it]" }
                            )
                        progressed = true
                    }
                }

                if (results.keys.containsAll(peersToResolve.map { it.publicKey })) {
                    Timber.d("All peers resolved")
                    return@coroutineScope results
                }

                if (!progressed) {
                    delay(delayMs.milliseconds)
                    delayMs = (delayMs * 2).coerceAtMost(MAX_BACKOFF)
                } else {
                    delayMs = 500L // reset after we have progressed
                }
            }
            return@coroutineScope results
        }

    companion object {
        private const val MAX_BACKOFF = 30_000L
    }
}
