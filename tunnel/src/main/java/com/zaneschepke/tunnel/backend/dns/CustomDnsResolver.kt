package com.zaneschepke.tunnel.backend.dns

import android.net.Network
import com.zaneschepke.tunnel.model.DnsBoostrapConfig
import com.zaneschepke.tunnel.model.DnsBootstrapResult
import com.zaneschepke.tunnel.util.DnsHostUtils
import timber.log.Timber

class CustomDnsResolver(
    private val dnsConfig: DnsBoostrapConfig,
    private val bypass: Boolean,
    network: Network,
) : PeerResolver {

    private val systemResolver = AndroidNetworkResolver(network)

    override suspend fun resolve(host: String): DnsBootstrapResult {

        val upstream = dnsConfig.upstream
        if (upstream.isNullOrBlank()) {
            Timber.w("Custom DNS mode selected but no upstream configured")
            return DnsBootstrapResult()
        }

        val resolvedUpstream =
            if (DnsHostUtils.needsResolution(upstream)) {
                Timber.d("Upstream DNS needs resolution, resolving via system resolver")
                val hostToResolve = DnsHostUtils.extractHost(upstream)

                val resolutionResult = systemResolver.resolve(hostToResolve)

                val ip = resolutionResult.ipv4.firstOrNull() ?: resolutionResult.ipv6.firstOrNull()
                if (ip == null) {
                    Timber.w("Failed to resolve custom DNS upstream host: $upstream")
                    return DnsBootstrapResult()
                }

                DnsHostUtils.replaceHostWithIP(upstream, ip)
            } else {
                upstream
            }

        Timber.d("Using custom resolver with resolved upstream $resolvedUpstream")

        return try {
            NativeDnsResolver.resolveHostBootstrap(
                host = host,
                protocol = dnsConfig.protocol,
                resolvedUpstream = resolvedUpstream,
                originalUpstream = upstream,
                bypass = bypass,
            )
        } catch (e: Exception) {
            Timber.w(e, "Custom DNS resolution failed for host=$host upstream=$resolvedUpstream")
            DnsBootstrapResult()
        }
    }
}
