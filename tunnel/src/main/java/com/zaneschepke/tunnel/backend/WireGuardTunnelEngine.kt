package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.ProxyConfig
import com.zaneschepke.tunnel.service.ServiceManager
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.tunnel.state.EngineStartResult
import com.zaneschepke.tunnel.util.BackendException
import com.zaneschepke.tunnel.util.PortUtils
import com.zaneschepke.tunnel.util.withRealEndpoint
import com.zaneschepke.tunnel.util.withWsTunnelLocalEndpoint
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import com.zaneschepke.wstunnel.WsTunnelConfig
import java.util.UUID

internal class WireGuardTunnelEngine(private val serviceManager: ServiceManager) : TunnelEngine {

    override suspend fun start(tunnelId: Int, mode: BackendMode): EngineStartResult {

        val ifName = WGT_INTERFACE_PREFIX + tunnelId

        // guard against static listenPort issues
        val listenPort = mode.config.`interface`.listenPort
        if (listenPort != null) {
            PortUtils.waitForUdpPortAvailable(listenPort)
        }

        var resolvedMode = mode

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
                    val config =
                        mode.wsTunnelConfig?.let { partial ->
                            val (rewrittenConfig, effectiveWsTunnelConfig) =
                                startWsTunnelBridge(service, mode.config, partial)
                            // Preserve mode.config as-is (it may already be DNS-resolved by the
                            // caller) - only the wsTunnelConfig's placeholder localPort/
                            // remoteHost/remotePort get filled in with their concrete values, so
                            // that later updatePeers()/getActiveConfig() calls (which read the
                            // mode stored on the ActiveTunnel record) see the real bridge port
                            // instead of the placeholder 0.
                            resolvedMode = mode.copy(wsTunnelConfig = effectiveWsTunnelConfig)
                            rewrittenConfig
                        } ?: mode.config
                    startVpnTunnel(ifName, config, service.detachVpnTunnelFd())
                }
            }

        if (handle < 0) {
            throw BackendException.InternalError("Native start failed: $handle")
        }

        return EngineStartResult(
            tunnelId = tunnelId,
            handle = handle,
            interfaceName = ifName,
            mode = resolvedMode,
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
        // When a WSTunnel bridge is active, wireguard-go's peer endpoint must always stay pinned
        // to the local bridge - DNS re-resolution (from DDNS/seamless recovery) is computed
        // against the *real* hostname for the bridge's benefit, but must never be written back
        // into wireguard-go itself, or it'd bypass the bridge it's meant to go through.
        val effectivePeers =
            if (mode is BackendMode.Vpn && mode.wsTunnelConfig != null) {
                val localEndpoint = "127.0.0.1:${mode.wsTunnelConfig.localPort}"
                peers.map { it.copy(endpoint = localEndpoint) }
            } else {
                peers
            }

        val config = mode.config.copy(peers = effectivePeers)

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
        val activeConfig = rawConfig?.let { ActiveConfig.parseFromIpc(it) } ?: return null

        // wireguard-go only ever sees 127.0.0.1:<localPort> in this mode - show the user the real
        // server endpoint instead, since that's what they actually configured and care about.
        return if (mode is BackendMode.Vpn && mode.wsTunnelConfig != null) {
            activeConfig.withRealEndpoint(mode.wsTunnelConfig)
        } else {
            activeConfig
        }
    }

    override suspend fun stop(handle: Int, mode: BackendMode) {
        when (mode) {
            is BackendMode.Proxy.Standard -> stopProxyTunnel(handle)
            is BackendMode.Vpn -> stopVpnTunnel(handle, mode)
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

    private suspend fun stopVpnTunnel(handle: Int, mode: BackendMode.Vpn) {
        VpnBackend.awgTurnOff(handle)
        if (mode.wsTunnelConfig != null) {
            serviceManager.getVpnService().stopWsTunnelBridge()
        }
    }

    /**
     * Starts the local WSTunnel bridge for a Vpn-mode tunnel and returns the config with its peer
     * endpoint(s) rewritten to point at the local bridge, paired with the *effective*
     * WsTunnelConfig (concrete localPort/remoteHost/remotePort filled in). The caller must persist
     * the effective config back onto the mode it stores for this tunnel (via EngineStartResult) -
     * otherwise later updatePeers()/getActiveConfig() calls would only see [partial]'s placeholder
     * localPort of 0.
     */
    private suspend fun startWsTunnelBridge(
        service: VpnService,
        config: Config,
        partial: WsTunnelConfig,
    ): Pair<Config, WsTunnelConfig> {
        val realEndpoint =
            config.peers.firstOrNull()?.endpoint
                ?: throw BackendException.InternalError(
                    "WSTunnel enabled but tunnel config has no peer endpoint to bridge"
                )

        val remoteHost =
            realEndpoint.substringBeforeLast(":").removePrefix("[").removeSuffix("]")
        val remotePort =
            realEndpoint.substringAfterLast(":").toIntOrNull()
                ?: throw BackendException.InternalError(
                    "Could not parse port from peer endpoint: $realEndpoint"
                )

        val localPort = PortUtils.getAvailableUdpPort(VpnService.WSTUNNEL_TRAFFIC_TAG)

        val effectiveConfig =
            partial.copy(localPort = localPort, remoteHost = remoteHost, remotePort = remotePort)

        service.startWsTunnelBridge(effectiveConfig)

        return config.withWsTunnelLocalEndpoint(localPort) to effectiveConfig
    }

    private fun startVpnTunnel(ifName: String, config: Config, fd: Int?): Int {
        val tunFd = fd ?: throw BackendException.Unauthorized("Failed to create tun interface")

        val handle =
            VpnBackend.awgTurnOn(ifName, tunFd, config.asQuickString(), serviceManager.uapiPath)
        if (handle < 0) {
            throw BackendException.InternalError("Internal native error with code: $handle")
        }
        return handle
    }

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
    }
}
