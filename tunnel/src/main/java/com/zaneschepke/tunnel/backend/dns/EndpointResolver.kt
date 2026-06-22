package com.zaneschepke.tunnel.backend.dns

import android.net.Network
import com.zaneschepke.networkmonitor.ConnectivityState
import com.zaneschepke.networkmonitor.PrivateDnsMode
import com.zaneschepke.networkmonitor.StableNetworkEngine
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.DnsBoostrapConfig
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
                val network = snapshot?.activeNetwork?.network ?: continue

                val dnsMode = getDnsMode()
                val bypassNeeded = mode is BackendMode.Vpn || isKillSwitchEnabled()
                var progressed = false

                for (peer in peersToResolve) {
                    if (results.containsKey(peer.publicKey)) continue
                    val host = peer.endpoint?.substringBeforeLast(":") ?: continue

                    val dnsResult =
                        when (dnsMode) {
                            is DnsBoostrapMode.Custom -> {
                                resolveWithCustomConfig(dnsMode.config, host, bypassNeeded)
                            }
                            is DnsBoostrapMode.System -> {
                                resolveWithSystemStrategy(snapshot, network, host, bypassNeeded)
                            }
                        }

                    if (
                        dnsResult != null &&
                            (dnsResult.ipv4.isNotEmpty() || dnsResult.ipv6.isNotEmpty())
                    ) {
                        results[peer.publicKey] =
                            dnsResult.copy(ipv6 = dnsResult.ipv6.map { "[$it]" })
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

    private suspend fun resolveWithSystemStrategy(
        snapshot: ConnectivityState,
        network: Network,
        host: String,
        bypass: Boolean,
    ): DnsBootstrapResult? {
        val dnsInfo = snapshot.underlyingDnsInfo
        val hasDnsServers = dnsInfo.servers.isNotEmpty()
        val hasPrivateDnsHostname =
            dnsInfo.privateDnsMode == PrivateDnsMode.HOSTNAME &&
                !dnsInfo.privateDnsHostname.isNullOrBlank()

        return when {
            // Private DNS hostname, use DoT/DoH via custom resolver
            hasPrivateDnsHostname -> {
                val hostname = dnsInfo.privateDnsHostname!!
                val config =
                    DnsBoostrapConfig.SPECIAL_ANDROID_DOH_SERVERS[hostname]?.let {
                        DnsBoostrapConfig.DoH(it)
                    } ?: DnsBoostrapConfig.DoT(hostname)

                Timber.d("System and Private DNS, using ${config.protocol} for $host")
                resolveWithCustomConfig(config, host, bypass)
            }

            // Normal system DNS
            hasDnsServers -> {
                try {
                    Timber.d("Using system DNS with network provided DNS servers")
                    AndroidNetworkResolver(network).resolve(host)
                } catch (e: Exception) {
                    Timber.w(e, "AndroidNetworkResolver failed for $host")
                    null
                }
            }

            // No DNS servers on network, fall back to custom with well known
            else -> {
                Timber.d("No DNS servers on network, falling back to public DNS for $host")
                val publicConfig = DnsBoostrapConfig.Plain(DnsBoostrapConfig.DEFAULT_PLAIN_UPSTREAM)
                resolveWithCustomConfig(publicConfig, host, bypass)
            }
        }
    }

    private suspend fun resolveWithCustomConfig(
        config: DnsBoostrapConfig,
        host: String,
        bypass: Boolean,
    ): DnsBootstrapResult? {
        val upstream =
            config.upstream
                ?: when (config) {
                    is DnsBoostrapConfig.DoH -> DnsBoostrapConfig.DEFAULT_DOH_UPSTREAM
                    is DnsBoostrapConfig.DoT -> DnsBoostrapConfig.DEFAULT_DOT_UPSTREAM
                    is DnsBoostrapConfig.Plain -> DnsBoostrapConfig.DEFAULT_PLAIN_UPSTREAM
                }

        return try {
            CustomDnsResolver(config, bypass).resolve(host)
        } catch (e: Exception) {
            Timber.w(
                e,
                "DNS resolution failed for host=%s protocol=%s upstream=%s bypass=%s",
                host,
                config.protocol,
                upstream,
                bypass,
            )
            null
        }
    }

    companion object {
        private const val MAX_BACKOFF = 30_000L
    }
}
