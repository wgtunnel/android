package com.zaneschepke.hevtunnel

data class HevTunnelConfig(
    val mtu: Int,
    val ipv4: String,
    val ipv6: String,
    val address: String,
    val port: Int,
    val username: String,
    val password: String,
)
