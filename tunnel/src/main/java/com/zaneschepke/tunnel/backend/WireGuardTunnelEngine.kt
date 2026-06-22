package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.ProxyBackend
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.VpnBackend
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.ProxyConfig
import com.zaneschepke.tunnel.service.ServiceHolder
import com.zaneschepke.tunnel.service.VpnService
import com.zaneschepke.tunnel.state.EngineStartResult
import com.zaneschepke.tunnel.util.BackendException
import com.zaneschepke.tunnel.util.PortUtils
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import java.util.UUID

internal class WireGuardTunnelEngine(private val serviceHolder: ServiceHolder) : TunnelEngine {

    override suspend fun start(tunnel: Tunnel, mode: BackendMode, fd: Int?): EngineStartResult {

        val ifName = WGT_INTERFACE_PREFIX + tunnel.id

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
                    startVpnTunnel(ifName, mode.config, fd)
                }
            }

        if (handle < 0) {
            throw BackendException.InternalError("Native start failed: $handle")
        }

        return EngineStartResult(
            tunnelId = tunnel.id,
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
        val service = serviceHolder.getVpnService()
        service.stopHevSocks5Bridge()
    }

    private fun stopProxyTunnel(handle: Int) {
        ProxyBackend.awgTurnProxyTunnelOff(handle)
    }

    private fun stopVpnTunnel(handle: Int) {
        VpnBackend.awgTurnOff(handle)
    }

    private fun startVpnTunnel(ifName: String, config: Config, fd: Int?): Int {
        val tunFd = fd ?: throw BackendException.Unauthorized("Failed to create tun interface")

        val handle =
            VpnBackend.awgTurnOn(ifName, tunFd, config.asQuickString(), serviceHolder.uapiPath)
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
                serviceHolder.uapiPath,
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

            serviceHolder.getVpnService().startHevSocks5Bridge(port, pass)
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
