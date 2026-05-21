package com.zaneschepke.tunnel.backend

import com.zaneschepke.tunnel.ProxyBackend
import com.zaneschepke.tunnel.Tunnel
import com.zaneschepke.tunnel.VpnBackend
import com.zaneschepke.tunnel.model.BackendMode
import com.zaneschepke.tunnel.model.ProxyConfig
import com.zaneschepke.tunnel.state.EngineStartResult
import com.zaneschepke.tunnel.state.EngineState
import com.zaneschepke.tunnel.state.NativeTunnelStatus
import com.zaneschepke.tunnel.util.BackendException
import com.zaneschepke.wireguardautotunnel.parser.ActiveConfig
import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.parser.PeerSection
import java.io.IOException
import java.net.ServerSocket
import java.util.UUID
import kotlinx.coroutines.flow.Flow

internal class WireGuardTunnelEngine(
    private val serviceHolder: ServiceHolder,
    stateProvider: EngineStateProvider,
) : TunnelEngine {

    private val proxyPass = UUID.randomUUID().toString()

    override val status: Flow<NativeTunnelStatus> = serviceHolder.nativeStatuses

    override val state: Flow<EngineState> = stateProvider.state

    override suspend fun start(tunnel: Tunnel, mode: BackendMode): EngineStartResult {

        val ifName = WGT_INTERFACE_PREFIX + tunnel.id

        val (config, removedPeerEndpoint) = buildConfig(mode)

        val handle =
            when (mode) {
                is BackendMode.Proxy.KillSwitchPrimary -> {
                    val proxyConfig = buildBridgeProxyConfig()
                    startProxyTunnel(ifName, config, proxyConfig, true)
                }
                is BackendMode.Proxy.Standard -> {
                    val proxyConfig = mode.proxyConfig
                    startProxyTunnel(ifName, config, proxyConfig, false)
                }
                is BackendMode.Vpn -> {
                    startVpnTunnel(tunnel, ifName, config)
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
            removedPeerEndpoint = removedPeerEndpoint,
        )
    }

    private fun buildConfig(mode: BackendMode): Pair<Config, Boolean> {
        var removedPeerEndpoint = false
        return mode.config.copy(
            peers =
                mode.config.peers.map { peer ->
                    if (!peer.isStaticallyConfigured) {
                        removedPeerEndpoint = true
                        rewriteDynamicEndpoint(peer)
                    } else peer
                }
        ) to removedPeerEndpoint
    }

    private fun buildBridgeProxyConfig(): ProxyConfig {
        return ProxyConfig(
            socks5 =
                ProxyConfig.Socks5(
                    port = getAvailablePort(),
                    username = LOCKDOWN_USER,
                    password = proxyPass,
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

    @Throws(IOException::class)
    private fun getAvailablePort(): Int {
        ServerSocket(0).use { socket ->
            socket.setReuseAddress(true)
            return socket.getLocalPort()
        }
    }

    // omit peer endpoint while boostrapping
    private fun rewriteDynamicEndpoint(peer: PeerSection): PeerSection {
        return peer.copy(endpoint = null)
    }

    override suspend fun stop(handle: Int, mode: BackendMode) {
        when (mode) {
            is BackendMode.Proxy -> {
                ProxyBackend.awgTurnProxyTunnelOff(handle)
            }
            is BackendMode.Vpn -> {
                VpnBackend.awgTurnOff(handle)
            }
        }
    }

    private fun startVpnTunnel(tunnel: Tunnel, ifName: String, config: Config): Int {

        val service = serviceHolder.getVpnService()

        val fd =
            service.createTunInterface(tunnel, config)?.detachFd()
                ?: throw BackendException.Unauthorized("Failed to create tun interface")

        val handle =
            VpnBackend.awgTurnOn(ifName, fd, config.asQuickString(), serviceHolder.uapiPath)

        if (handle < 0) {
            throw BackendException.InternalError("Internal native error with code: $handle")
        }

        service.protect(VpnBackend.awgGetSocketV4(handle))
        service.protect(VpnBackend.awgGetSocketV6(handle))

        return handle
    }

    private fun startProxyTunnel(
        ifName: String,
        config: Config,
        proxyConfig: ProxyConfig,
        withBridge: Boolean,
    ): Int {

        val quickConfig = buildProxiedQuickString(config, proxyConfig)

        if (!withBridge) {
            serviceHolder.getTunnelService()
        }

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

        if (withBridge) {
            serviceHolder.getVpnService().startHevSocks5Bridge()
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
        const val LOCKDOWN_USER = "local"
        const val WGT_INTERFACE_PREFIX = "wgtun"
    }
}
