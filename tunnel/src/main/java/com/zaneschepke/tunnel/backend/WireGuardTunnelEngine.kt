package com.zaneschepke.tunnel.backend

import com.zaneschepke.networkmonitor.StableNetworkEngine
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.ProxyConfig
import com.zaneschepke.tunnel.service.ServiceManager
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.tunnel.state.EngineStartResult
import com.zaneschepke.tunnel.util.BackendException
import com.zaneschepke.tunnel.util.PortUtils
import com.zaneschepke.tunnel.util.parseDns
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import java.util.UUID
import timber.log.Timber

internal class WireGuardTunnelEngine(
    private val serviceManager: ServiceManager,
    private val stableNetworkEngine: StableNetworkEngine,
) : TunnelEngine {

    override suspend fun start(
        tunnelId: Int,
        mode: BackendMode,
        splitDnsDomains: Set<String>,
    ): EngineStartResult {

        val ifName = WGT_INTERFACE_PREFIX + tunnelId

        // guard against static listenPort issues
        val listenPort = mode.config.`interface`.listenPort
        if (listenPort != null) {
            PortUtils.waitForUdpPortAvailable(listenPort)
        }

        val handle =
            when (mode) {
                is BackendMode.Proxy.KillSwitchPrimary -> {
                    val proxyConfig = buildBridgeProxyConfig()
                    startProxyTunnel(ifName, mode.config, proxyConfig, true)
                }
                is BackendMode.Proxy.Standard -> {
                    val proxyConfig = mode.proxyConfig

                    proxyConfig.socks5?.port?.let { port ->
                        if (!PortUtils.isPortAvailable(port)) {
                            throw BackendException.Socks5PortUnavailable(
                                "SOCKS5 port $port is already in use.",
                                port,
                            )
                        }
                    }

                    proxyConfig.http?.port?.let { port ->
                        if (!PortUtils.isPortAvailable(port)) {
                            throw BackendException.HttpPortUnavailable(
                                "HTTP listener port $port is already in use.",
                                port,
                            )
                        }
                    }
                    startProxyTunnel(ifName, mode.config, proxyConfig, false)
                }
                is BackendMode.Vpn -> {
                    val service = serviceManager.getVpnService()
                    startVpnTunnel(
                        splitDnsDomains,
                        ifName,
                        mode.config,
                        service.detachVpnTunnelFd(),
                    )
                }
            }

        if (handle < 0) {
            throw BackendException.InternalError("Native start failed: $handle")
        }

        return EngineStartResult(
            tunnelId = tunnelId,
            handle = handle,
            interfaceName = ifName,
            mode = mode,
        )
    }

    private fun buildBridgeProxyConfig(): ProxyConfig {
        return ProxyConfig(
            socks5 =
                ProxyConfig.Socks5(
                    port = PortUtils.getAvailableTcpPort(VpnService.HEV_BRIDGE_TRAFFIC_TAG),
                    username = VpnService.LOCKDOWN_USERNAME,
                    password = UUID.randomUUID().toString(),
                )
        )
    }

    override suspend fun updatePeers(handle: Int, mode: BackendMode, peers: List<PeerSection>) {
        val config = mode.config.copy(peers = peers)

        when (mode) {
            is BackendMode.Proxy -> {
                ProxyBackend.awgUpdateProxyTunnelPeers(handle, config.asQuickString())
            }
            is BackendMode.Vpn -> {
                VpnBackend.awgUpdateTunnelPeers(handle, config.asQuickString())
            }
        }
    }

    override suspend fun getActiveConfig(handle: Int, mode: BackendMode): ActiveConfig? {
        val rawConfig =
            when (mode) {
                is BackendMode.Proxy -> ProxyBackend.awgGetProxyConfig(handle)
                is BackendMode.Vpn -> VpnBackend.awgGetConfig(handle)
            }
        return rawConfig?.let { ActiveConfig.parseFromIpc(it) }
    }

    override suspend fun stop(handle: Int, mode: BackendMode) {
        when (mode) {
            is BackendMode.Proxy.Standard -> stopProxyTunnel(handle)
            is BackendMode.Vpn -> stopVpnTunnel(handle)
            is BackendMode.Proxy.KillSwitchPrimary -> stopKillSwitchPrimaryTunnel(handle)
        }
    }

    private suspend fun stopKillSwitchPrimaryTunnel(handle: Int) {
        ProxyBackend.awgTurnProxyTunnelOff(handle)
        val service = serviceManager.getVpnService()
        service.stopHevSocks5Bridge()
    }

    private fun stopProxyTunnel(handle: Int) {
        ProxyBackend.awgTurnProxyTunnelOff(handle)
    }

    private fun stopVpnTunnel(handle: Int) {
        VpnBackend.awgTurnOff(handle)
    }

    private fun startVpnTunnel(
        splitDnsDomains: Set<String>,
        ifName: String,
        config: Config,
        fd: Int?,
    ): Int {
        val tunFd = fd ?: throw BackendException.Unauthorized("Failed to create tun interface")

        val (splitDnsDomainsCsv, splitDnsSystemServers) = resolveSplitDns(splitDnsDomains, config)

        val handle =
            VpnBackend.awgTurnOn(
                ifName,
                tunFd,
                config.asQuickString(),
                serviceManager.uapiPath,
                splitDnsDomainsCsv,
                splitDnsSystemServers,
            )
        if (handle < 0) {
            throw BackendException.InternalError("Internal native error with code: $handle")
        }
        return handle
    }

    /**
     * Computes the split-tunnel DNS parameters passed to the native layer.
     *
     * Split DNS is only enabled when the tunnel has both a configured DNS server (an IP in the
     * interface DNS) and a non-empty list of domains. In that case matching domains are resolved
     * through the tunnel DNS server while all other queries are resolved against the underlying
     * system DNS servers. Returns empty strings to disable interception (native then routes all DNS
     * to the tunnel DNS server, the default behavior).
     */
    private fun resolveSplitDns(
        splitDnsDomains: Set<String>,
        config: Config,
    ): Pair<String, String> {
        if (splitDnsDomains.isEmpty()) return EMPTY_SPLIT_DNS

        val hasTunnelDnsServer =
            config.`interface`.dns?.parseDns()?.dnsServers?.isNotEmpty() == true
        if (!hasTunnelDnsServer) {
            Timber.w("Split DNS domains set but tunnel has no DNS server configured; ignoring")
            return EMPTY_SPLIT_DNS
        }

        val systemServers = withDefaultServers(currentSystemDnsServers())

        return splitDnsDomains.joinToString(",") to systemServers.joinToString(",")
    }

    override fun updateSplitDnsServers(handle: Int, servers: List<String>) {
        val merged = withDefaultServers(servers)
        val result = VpnBackend.awgSetSplitDnsServers(handle, merged.joinToString(","))
        if (result == 0) {
            Timber.d("Split DNS system servers updated for handle %d: %s", handle, merged)
        }
    }

    // Public resolvers are appended as a last resort for non-matching (public) queries
    // only; queries matching the split DNS domain list always go through the tunnel.
    private fun withDefaultServers(servers: List<String>): List<String> =
        (servers + DEFAULT_SYSTEM_DNS_SERVERS).distinct()

    private fun currentSystemDnsServers(): List<String> =
        stableNetworkEngine.stableState.value?.state?.underlyingDnsInfo?.servers.orEmpty()

    private suspend fun startProxyTunnel(
        ifName: String,
        config: Config,
        proxyConfig: ProxyConfig,
        withBridge: Boolean,
    ): Int {
        val quickConfig = buildProxiedQuickString(config, proxyConfig)

        val handle =
            ProxyBackend.awgStartProxy(
                ifName,
                quickConfig,
                serviceManager.uapiPath,
                if (withBridge) 1 else 0,
            )
        if (handle < 0) {
            throw BackendException.InternalError("Internal native error")
        }

        // Start HEV bridge after the proxy tunnel is up
        if (withBridge) {
            val port =
                proxyConfig.socks5?.port
                    ?: throw BackendException.InternalError(
                        "Bridge port not set for kill switch proxy config"
                    )
            val pass =
                proxyConfig.socks5.password
                    ?: throw BackendException.InternalError(
                        "Bridge pass not set for kill switch proxy config"
                    )

            serviceManager.getVpnService().startHevSocks5Bridge(port, pass)
        }

        return handle
    }

    private fun buildProxiedQuickString(config: Config, proxyConfig: ProxyConfig): String {
        return buildString {
            append(config.asQuickString())
            append(System.lineSeparator())
            append(proxyConfig.toQuickString())
        }
    }

    companion object {
        const val WGT_INTERFACE_PREFIX = "wgtun"

        private val EMPTY_SPLIT_DNS = "" to ""
        // Public resolvers used as a last-resort fallback for non-matching (public) queries.
        private val DEFAULT_SYSTEM_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8")
    }
}
