package com.zaneschepke.wstunnel

/**
 * Configuration for bridging a single WireGuard peer's UDP traffic through a remote WSTunnel
 * server. WireGuard itself is never made aware of WSTunnel - it just gets pointed at
 * 127.0.0.1:[localPort] as its peer endpoint, and this bridge process relays those datagrams to
 * [serverUrl] over a WebSocket, which the wstunnel server unwraps back to UDP toward
 * [remoteHost]:[remotePort].
 */
data class WsTunnelConfig(
    /** Local UDP port WireGuard's Endpoint will be rewritten to point at. */
    val localPort: Int,
    /** The real WireGuard server host (the original peer Endpoint host). */
    val remoteHost: String,
    /** The real WireGuard server UDP port (the original peer Endpoint port). */
    val remotePort: Int,
    /** The WSTunnel server address, e.g. "wss://vpn.example.com:443". */
    val serverUrl: String,
    /** Optional path prefix used to make the WS upgrade request look less distinctive. */
    val httpUpgradePathPrefix: String? = null,
    /** 0 disables idle timeout, matching wstunnel's own udp timeout_sec query param default. */
    val udpTimeoutSec: Int = 0,
    /** Extra TLS SNI override, useful for domain-fronting style setups. */
    val tlsSni: String? = null,
)
